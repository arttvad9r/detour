package dev.triplet.app.tile

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.triplet.app.R
import dev.triplet.app.TripletApp
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import dev.triplet.app.vpn.resolveEffectiveRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Плитка в шторке: подключение/отключение без открытия приложения. */
@SuppressLint("StartActivityAndCollapseDeprecated")
class DetourTile : TileService() {

    private val store by lazy { (application as TripletApp).routesStore }
    private var jobs: List<Job> = emptyList()
    private var routed = 0

    override fun onStartListening() {
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        jobs = listOf(
            scope.launch { VpnController.state.collect { update(it) } },
            scope.launch {
                store.settings
                    .map { it?.routes.orEmpty() }
                    .distinctUntilChanged()
                    .collect { routes ->
                        routed = if (routes.isEmpty()) {
                            0
                        } else {
                            withContext(Dispatchers.IO) {
                                resolveEffectiveRoutes(packageManager, routes).packages.size
                            }
                        }
                        update(VpnController.state.value)
                    }
            },
        )
    }

    override fun onStopListening() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    override fun onClick() {
        val ctx = applicationContext
        when (VpnController.state.value) {
            VpnState.Active -> VpnController.stop(ctx)
            VpnState.Starting -> Unit
            else -> {
                if (android.net.VpnService.prepare(ctx) == null) VpnController.startNow(ctx)
                else {
                    val intent = Intent(ctx, dev.triplet.app.vpn.VpnConsentActivity::class.java)
                    if (Build.VERSION.SDK_INT >= 34) {
                        startActivityAndCollapse(PendingIntent.getActivity(
                            this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ))
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(intent)
                    }
                }
            }
        }
        update(VpnController.state.value)
    }

    private fun update(state: VpnState) {
        val tile = qsTile ?: return
        when (state) {
            VpnState.Active -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = getString(R.string.tile_connected)
            }
            VpnState.Starting -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_connecting)
            }
            is VpnState.Failed -> {
                // UNAVAILABLE tiles are not actionable. Keep the tile tappable so
                // a failed connection can be retried directly from Quick Settings.
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_error)
            }
            VpnState.Idle -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = if (routed > 0) {
                    resources.getQuantityString(R.plurals.tile_routed, routed, routed)
                } else {
                    getString(R.string.tile_disconnected)
                }
            }
        }
        tile.updateTile()
    }
}
