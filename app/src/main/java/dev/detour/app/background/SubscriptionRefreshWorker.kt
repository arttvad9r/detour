package dev.detour.app.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.detour.app.DetourApp
import dev.detour.app.core.ParseResult
import dev.detour.app.core.SubscriptionRefreshPolicy
import dev.detour.app.core.VlessKeyParser
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.TriSettings
import dev.detour.app.vpn.VpnController
import dev.detour.app.vpn.VpnState
import dev.detour.engine.engine.Engine
import java.util.concurrent.TimeUnit

class SubscriptionRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val app = applicationContext as? DetourApp ?: return Result.failure()
        val settings = app.routesStore.snapshot()
        if (settings.activeVpn != VpnProfileKind.SUBSCRIPTION) return Result.success()

        val key = settings.vlessKeys.active?.takeIf { it.id == profileId }
            ?: return Result.success()
        val intervalHours = SubscriptionRefreshPolicy.effectiveIntervalHours(
            key.subscriptionUpdateIntervalHours,
        ) ?: return Result.success()
        val parsed = VlessKeyParser.parse(key.uri) as? ParseResult.Ok ?: return Result.success()
        val subscriptionUrl = parsed.profile.subscriptionUrl ?: return Result.success()
        val now = System.currentTimeMillis()
        if (!SubscriptionRefreshPolicy.isDue(key.subscriptionUpdatedAt, intervalHours, now)) {
            return Result.success()
        }

        val prepared = runCatching {
            Engine.prepareSubscriptionProvider(subscriptionUrl, applicationContext.cacheDir.absolutePath)
        }.getOrNull().orEmpty()
        if (prepared.isBlank()) return Result.retry()

        val liveSubscription =
            VpnController.state.value == VpnState.Active && Engine.ready()
        if (liveSubscription) {
            val refreshed = runCatching { Engine.refreshSubscriptionProvider() }.isSuccess
            if (!refreshed) return Result.retry()
        }

        val actualNode = if (liveSubscription) {
            runCatching {
                Engine.subscriptionSelectedNode(applicationContext.cacheDir.absolutePath)
            }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val latest = app.routesStore.snapshot().vlessKeys.active
            ?.takeIf { it.id == profileId && it.uri == key.uri }
            ?: return Result.success()
        app.routesStore.updateVlessKey(
            latest.copy(
                selectedNode = actualNode ?: latest.selectedNode,
                subscriptionUpdatedAt = System.currentTimeMillis(),
            ),
        )
        return Result.success()
    }

    companion object {
        internal const val KEY_PROFILE_ID = "profile_id"
    }
}

object SubscriptionRefreshScheduler {
    private const val UNIQUE_ACTIVE_SUBSCRIPTION_WORK = "detour-active-subscription-refresh"
    private const val UNIQUE_ACTIVE_SUBSCRIPTION_OPPORTUNISTIC_WORK =
        "detour-active-subscription-refresh-opportunistic"

    fun reconcile(context: Context, settings: TriSettings) {
        val workManager = WorkManager.getInstance(context)
        val active = settings.vlessKeys.active
        val intervalHours = active?.subscriptionUpdateIntervalHours
        val isSubscription = active != null &&
            settings.activeVpn == VpnProfileKind.SUBSCRIPTION &&
            (VlessKeyParser.parse(active.uri) as? ParseResult.Ok)?.profile?.isSubscription == true
        if (!isSubscription || intervalHours == null) {
            workManager.cancelUniqueWork(UNIQUE_ACTIVE_SUBSCRIPTION_WORK)
            workManager.cancelUniqueWork(UNIQUE_ACTIVE_SUBSCRIPTION_OPPORTUNISTIC_WORK)
            return
        }

        val effectiveHours = SubscriptionRefreshPolicy.effectiveIntervalHours(intervalHours)
            ?: run {
                workManager.cancelUniqueWork(UNIQUE_ACTIVE_SUBSCRIPTION_WORK)
                workManager.cancelUniqueWork(UNIQUE_ACTIVE_SUBSCRIPTION_OPPORTUNISTIC_WORK)
                return
            }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val inputData = workDataOf(SubscriptionRefreshWorker.KEY_PROFILE_ID to active.id)
        val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(
            effectiveHours.toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_ACTIVE_SUBSCRIPTION_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )

        // reconcile() runs when the application observes the active subscription
        // configuration. If the cached subscription is already stale, do not wait
        // for the first periodic WorkManager window: enqueue the same guarded worker
        // immediately. The worker re-checks isDue(), so this remains idempotent.
        if (
            SubscriptionRefreshPolicy.isDue(
                active.subscriptionUpdatedAt,
                effectiveHours,
                System.currentTimeMillis(),
            )
        ) {
            val opportunisticRequest = OneTimeWorkRequestBuilder<SubscriptionRefreshWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()
            workManager.enqueueUniqueWork(
                UNIQUE_ACTIVE_SUBSCRIPTION_OPPORTUNISTIC_WORK,
                ExistingWorkPolicy.REPLACE,
                opportunisticRequest,
            )
        }
    }
}
