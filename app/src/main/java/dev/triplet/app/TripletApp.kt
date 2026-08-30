package dev.triplet.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.triplet.app.data.AppInventory
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal fun shouldRestartVpnForPackageChange(action: String?, replacing: Boolean): Boolean = when (action) {
    Intent.ACTION_PACKAGE_ADDED -> true
    Intent.ACTION_PACKAGE_REMOVED -> !replacing
    else -> false
}

class TripletApp : Application() {
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

            appScope.launch {
                if (packageName in routesStore.snapshot().routes) {
                    VpnController.restartIfActive(this@TripletApp)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        routesStore = RoutesStore(this)

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

        // Warm route metadata and tiny row icons off the main thread while the
        // user is still on Home. The first Routes transition can then draw its
        // real contents immediately instead of swapping placeholders mid-slide.
        appScope.launch { AppInventory.warm(this@TripletApp) }
    }
}
