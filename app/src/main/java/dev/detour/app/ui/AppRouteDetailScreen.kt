package dev.detour.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.detour.app.R
import dev.detour.app.core.AppRoute
import dev.detour.app.core.DpiArgs
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.RoutesStore
import dev.detour.app.data.TriSettings
import dev.detour.app.vpn.ResolvedRouteSnapshot
import dev.detour.app.vpn.VpnController
import dev.detour.app.vpn.VpnState
import dev.detour.app.vpn.resolveRouteSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class RouteReadiness {
    CHECKING, UNAVAILABLE, REMOVED, DIRECT, EXCLUDED, SHARED_UID, OTHER_CONFLICT, MISSING_PROFILE, INVALID_DPI, CONFIGURED,
}

internal fun routeReadiness(
    packageName: String,
    settings: TriSettings,
    snapshot: ResolvedRouteSnapshot,
    installed: Boolean,
    ownUid: Int,
): RouteReadiness {
    if (!installed) return RouteReadiness.REMOVED
    val route = settings.routes[packageName] ?: AppRoute.DIRECT
    if (route == AppRoute.DIRECT) return RouteReadiness.DIRECT
    val uid = snapshot.installedUids[packageName]
    if (uid == ownUid) return RouteReadiness.EXCLUDED
    if (uid in snapshot.effective.sharedUidConflict) return RouteReadiness.SHARED_UID
    if (snapshot.effective.sharedUidConflict.isNotEmpty()) return RouteReadiness.OTHER_CONFLICT
    if (packageName !in snapshot.effective.packages) return RouteReadiness.UNAVAILABLE
    // These are tunnel-wide prerequisites, even when this app uses the other route.
    if (snapshot.effective.vpnPackages.isNotEmpty() && !settings.activeVpnConfigured) {
        return RouteReadiness.MISSING_PROFILE
    }
    if (snapshot.effective.dpiPackages.isNotEmpty() && settings.preset == DpiPreset.CUSTOM &&
        !DpiArgs.isValid(settings.dpiCustomArgs)
    ) return RouteReadiness.INVALID_DPI
    return RouteReadiness.CONFIGURED
}

private data class RouteDetailSnapshot(
    val settings: TriSettings,
    val label: String,
    val readiness: RouteReadiness,
)

@Composable
internal fun AppRouteDetailDestination(
    packageName: String,
    store: RoutesStore,
    context: Context,
    onBack: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenDpi: () -> Unit,
    onOpenConnection: () -> Unit,
) {
    val settings by store.settings.collectAsStateWithLifecycle()
    val vpn by VpnController.state.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val detail by produceState<RouteDetailSnapshot?>(null, packageName, settings, revision) {
        value = null
        val current = settings ?: return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val info = try {
                    context.packageManager.getApplicationInfo(packageName, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
                val route = current.routes[packageName] ?: AppRoute.DIRECT
                val snapshot = resolveRouteSnapshot(context.packageManager,
                    if (route == AppRoute.DIRECT) emptyMap() else current.routes)
                RouteDetailSnapshot(current, info?.loadLabel(context.packageManager)?.toString() ?: packageName,
                    routeReadiness(packageName, current, snapshot, info != null, Process.myUid()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RouteDetailSnapshot(current, packageName, RouteReadiness.UNAVAILABLE)
            }
        }
    }
    AppRouteDetailScreen(
        packageName = packageName,
        label = detail?.label ?: packageName,
        route = (detail?.settings ?: settings)?.routes?.get(packageName) ?: AppRoute.DIRECT,
        readiness = detail?.readiness ?: RouteReadiness.CHECKING,
        vpnState = vpn,
        onBack = onBack,
        onOpenProfiles = onOpenProfiles,
        onOpenDpi = onOpenDpi,
        onOpenConnection = onOpenConnection,
        onRetry = { revision++ },
        profileName = detail?.settings?.takeIf { it.activeVpnConfigured }?.let {
            when (it.activeVpn) {
                VpnProfileKind.WARP -> it.wireGuardProfiles.active?.name
                VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION -> it.vlessKeys.active?.name
            }
        },
    )
}

@Composable
internal fun AppRouteDetailScreen(
    packageName: String,
    label: String,
    route: AppRoute,
    readiness: RouteReadiness,
    vpnState: VpnState,
    onBack: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenDpi: () -> Unit,
    onOpenConnection: () -> Unit,
    onRetry: () -> Unit,
    profileName: String? = null,
) {
    val c = detourColors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
        .testTag("app-route-detail")) {
        DetourBrandedHeader(stringResource(R.string.route_detail_title), onBack, hideBackInListDetail = false)
        DetourContentColumn(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(Spacing.space16),
                verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
                Text(label, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                Text(packageName, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                DetourCard {
                    Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                        Text(stringResource(R.string.route_detail_assigned), style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary)
                        Text(stringResource(routeLabel(route)), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                        DetourDetails(stringResource(R.string.route_detail_path_details)) {
                        Text(stringResource(when (route) {
                            AppRoute.DIRECT -> R.string.route_detail_direct_path
                            AppRoute.VPN -> R.string.route_detail_vpn_path
                            AppRoute.DPI -> R.string.route_detail_dpi_path
                        }), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        }
                    }
                }
                if (route == AppRoute.VPN && profileName != null) {
                    Text(stringResource(R.string.route_detail_profile_name, profileName),
                        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                if (readiness != RouteReadiness.DIRECT) DetourCard {
                    Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                        Text(stringResource(R.string.route_detail_readiness), style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary)
                        Text(stringResource(readinessText(readiness)), style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary, modifier = Modifier.testTag("route-readiness"))
                        when (readiness) {
                            RouteReadiness.MISSING_PROFILE -> DetourButton(stringResource(R.string.route_detail_profiles), onOpenProfiles)
                            RouteReadiness.INVALID_DPI -> DetourButton(stringResource(R.string.route_detail_dpi_settings), onOpenDpi)
                            RouteReadiness.UNAVAILABLE -> DetourButton(stringResource(R.string.action_retry), onRetry)
                            RouteReadiness.EXCLUDED, RouteReadiness.SHARED_UID, RouteReadiness.OTHER_CONFLICT ->
                                DetourButton(stringResource(R.string.route_detail_edit), onBack)
                            else -> Unit
                        }
                    }
                }
                Text(stringResource(when (vpnState) {
                    VpnState.Idle -> R.string.route_detail_idle
                    VpnState.Starting -> R.string.route_detail_starting
                    VpnState.Active -> R.string.route_detail_active
                    is VpnState.Failed -> R.string.route_detail_failed
                }), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                    modifier = Modifier.testTag("route-connection-state"))
                Text(stringResource(R.string.route_detail_not_live), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                if (route != AppRoute.DIRECT && readiness != RouteReadiness.REMOVED) {
                    DetourButton(stringResource(R.string.route_detail_connection), onOpenConnection)
                }
                if (route == AppRoute.DPI && readiness != RouteReadiness.INVALID_DPI) {
                    TextButton(onClick = onOpenDpi) { Text(stringResource(R.string.route_detail_dpi_settings)) }
                }
                if (readiness !in setOf(RouteReadiness.EXCLUDED, RouteReadiness.SHARED_UID, RouteReadiness.OTHER_CONFLICT)) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.route_detail_edit)) }
                }
            }
        }
    }
}

private fun readinessText(readiness: RouteReadiness): Int = when (readiness) {
    RouteReadiness.CHECKING -> R.string.route_detail_checking
    RouteReadiness.UNAVAILABLE -> R.string.route_detail_unavailable
    RouteReadiness.REMOVED -> R.string.route_detail_removed
    RouteReadiness.DIRECT -> R.string.route_detail_direct_ready
    RouteReadiness.EXCLUDED -> R.string.route_detail_excluded
    RouteReadiness.SHARED_UID -> R.string.route_detail_shared_uid
    RouteReadiness.OTHER_CONFLICT -> R.string.route_detail_other_conflict
    RouteReadiness.MISSING_PROFILE -> R.string.route_detail_missing_profile
    RouteReadiness.INVALID_DPI -> R.string.route_detail_invalid_dpi
    RouteReadiness.CONFIGURED -> R.string.route_detail_configured
}
