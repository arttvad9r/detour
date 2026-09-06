package dev.detour.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.detour.app.core.SubscriptionProviderMaterializer
import dev.detour.app.data.AppInventory
import dev.detour.app.data.RoutesStore
import dev.detour.app.vpn.VpnController
import dev.detour.app.vpn.VpnState
import dev.detour.app.vpn.resolveRouteSnapshot
import dev.detour.engine.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun shouldRestartVpnForPackageChange(action: String?, replacing: Boolean): Boolean = when (action) {
    Intent.ACTION_PACKAGE_ADDED -> true
    Intent.ACTION_PACKAGE_REMOVED -> !replacing
    else -> false
}

internal fun packageChangeAffectsRoutes(
    packageName: String,
    changedUid: Int,
    routedPackages: Set<String>,
    routedUids: Collection<Int?>,
): Boolean =
    packageName in routedPackages ||
        (changedUid >= 0 && routedUids.any { it == changedUid })

class DetourApp : Application() {
    lateinit var routesStore: RoutesStore
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            AppInventory.invalidate(packageName)

            // Package/component enablement changes can alter the launchable app
            // inventory but do not change package UID ownership, so only refresh
            // cached metadata for PACKAGE_CHANGED without restarting the tunnel.
            if (!shouldRestartVpnForPackageChange(
                    intent.action,
                    intent.getBooleanExtra(Intent.EXTRA_REPLACING, false),
                )) return

            val state = VpnController.state.value
            if (state != VpnState.Active && state != VpnState.Starting) return

            val changedUid = intent.getIntExtra(Intent.EXTRA_UID, -1)
            appScope.launch {
                val settings = routesStore.snapshot()
                val resolved = resolveRouteSnapshot(packageManager, settings.routes)
                if (packageChangeAffectsRoutes(
                        packageName = packageName,
                        changedUid = changedUid,
                        routedPackages = settings.routes.keys,
                        routedUids = resolved.installedUids.values,
                    )) {
                    VpnController.restartIfActive(this@DetourApp)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        routesStore = RoutesStore(this)
        SubscriptionProviderMaterializer.install { url ->
            runCatching {
                Engine.prepareSubscriptionProvider(url, cacheDir.absolutePath)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""
        }

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageChanges,
            packageFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        appScope.launch { AppInventory.warm(this@DetourApp) }
    }

    fun clearVpnSessionTimestampAsync() {
        appScope.launch {
            runCatching { routesStore.setSessionStartedAt(null) }
        }
    }
}
