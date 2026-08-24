package dev.triplet.app.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.triplet.app.TripletApp
import dev.triplet.app.core.AppRoute
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Плитка в шторке: подключение/отключение без открытия приложения. */
class DetourTile : TileService() {

    private val store by lazy { (application as TripletApp).routesStore }
    private var jobs: List<Job> = emptyList()
    private var routed = 0

    override fun onStartListening() {
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        jobs = listOf(
            scope.launch { VpnController.state.collect { update(it) } },
            scope.launch {
                store.settings.collect { s ->
                    routed = s?.routes?.count { it.value != AppRoute.DIRECT } ?: routed
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
                // Согласие VPN из плитки: системный диалог поверх шторки.
                VpnController.start(ctx) { intent ->
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
                tile.subtitle = "подключено"
            }
            VpnState.Starting -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "подключение…"
            }
            is VpnState.Failed -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = "ошибка"
            }
            VpnState.Idle -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = if (routed > 0) "$routed в туннеле" else "отключено"
            }
        }
        tile.updateTile()
    }
}
