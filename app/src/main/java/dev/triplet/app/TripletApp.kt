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

class TripletApp : Application() {
    lateinit var routesStore: RoutesStore
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            AppInventory.invalidate()

            // An in-place app update emits REMOVED(replacing=true) followed by
            // ADDED. Restart only after the replacement exists again, while a
            // real removal must rebuild the VPN allow-list immediately so its
            // old numeric UID can never be inherited by another package.
            if (
                intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) return

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
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageChanges,
            packageFilter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        // PackageManager discovery is useful only on the routes screen, so warm
        // it off the main thread while the user is on the home screen.
        appScope.launch { AppInventory.load(this@TripletApp) }
    }
}
