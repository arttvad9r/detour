package dev.triplet.app.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.triplet.app.TripletApp
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.SubscriptionRefreshPolicy
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.data.TriSettings
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import dev.triplet.engine.engine.Engine
import java.util.concurrent.TimeUnit

class SubscriptionRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KEY_PROFILE_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val app = applicationContext as? TripletApp ?: return Result.failure()
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

    fun reconcile(context: Context, settings: TriSettings) {
        val workManager = WorkManager.getInstance(context)
        val active = settings.vlessKeys.active
        val intervalHours = active?.subscriptionUpdateIntervalHours
        val isSubscription = active != null &&
            settings.activeVpn == VpnProfileKind.SUBSCRIPTION &&
            (VlessKeyParser.parse(active.uri) as? ParseResult.Ok)?.profile?.isSubscription == true
        if (!isSubscription || intervalHours == null) {
            workManager.cancelUniqueWork(UNIQUE_ACTIVE_SUBSCRIPTION_WORK)
            return
        }

        val effectiveHours = SubscriptionRefreshPolicy.effectiveIntervalHours(intervalHours)
            ?: run {
                workManager.cancelUniqueWork(UNIQUE_ACTIVE_SUBSCRIPTION_WORK)
                return
            }
        val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(
            effectiveHours.toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(SubscriptionRefreshWorker.KEY_PROFILE_ID to active.id))
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_ACTIVE_SUBSCRIPTION_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
