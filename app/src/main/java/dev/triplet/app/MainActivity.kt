package dev.triplet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.triplet.app.data.AppInventory
import dev.triplet.app.ui.AppShapes
import dev.triplet.app.ui.AppTheme
import dev.triplet.app.ui.AppTypography
import dev.triplet.app.ui.AppsScreen
import dev.triplet.app.ui.AppsViewModel
import dev.triplet.app.ui.BackupScreen
import dev.triplet.app.ui.BackupViewModel
import dev.triplet.app.ui.DetourColors
import dev.triplet.app.ui.DnsScreen
import dev.triplet.app.ui.DnsViewModel
import dev.triplet.app.ui.DpiScreen
import dev.triplet.app.ui.DpiViewModel
import dev.triplet.app.ui.HomeScreen
import dev.triplet.app.ui.HomeViewModel
import dev.triplet.app.ui.LocalDetourColors
import dev.triplet.app.ui.LocalDetourTheme
import dev.triplet.app.ui.Motion
import dev.triplet.app.ui.ProfilesViewModel
import dev.triplet.app.ui.SettingsMenuScreen
import dev.triplet.app.ui.SettingsMenuViewModel
import dev.triplet.app.ui.ThemeScreen
import dev.triplet.app.ui.VlessKeyScreen
import dev.triplet.app.ui.colorSchemeFor
import dev.triplet.app.ui.configureAdaptiveRefresh
import dev.triplet.app.ui.detourHighRefresh
import dev.triplet.app.vpn.AutoConnectCoordinator
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import dev.triplet.app.vpn.resolveEffectiveRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private object Route {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ROUTES = "routes"
    const val VLESS = "vless"
    const val DPI = "dpi"
    const val THEME = "theme"
    const val DNS = "dns"
    const val BACKUP = "backup"
}

internal fun themeTransitionDuration(previousDark: Boolean, targetDark: Boolean): Int =
    if (previousDark == targetDark) Motion.THEME_MS else 0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureAdaptiveRefresh(window)
        val store = (application as TripletApp).routesStore
        val appContext = applicationContext
        setContent {
            val settings by store.settings.collectAsStateWithLifecycle()
            val theme = AppTheme.byId(settings?.themeId ?: "")
            val target = theme.colors
            var previousDark by remember { mutableStateOf(theme.dark) }
            val themeAnimation = tween<Color>(themeTransitionDuration(previousDark, theme.dark))

            // Interpolate within the same brightness mode. A continuous light↔dark
            // palette interpolation necessarily crosses a frame where foreground and
            // background luminance converge, so cross-mode changes snap atomically.
            val animatedColors = DetourColors(
                background = animateColorAsState(target.background, themeAnimation, label = "themeBackground").value,
                surface = animateColorAsState(target.surface, themeAnimation, label = "themeSurface").value,
                surfaceSoft = animateColorAsState(target.surfaceSoft, themeAnimation, label = "themeSurfaceSoft").value,
                surfaceSelected = animateColorAsState(target.surfaceSelected, themeAnimation, label = "themeSelected").value,
                textPrimary = animateColorAsState(target.textPrimary, themeAnimation, label = "themeTextPrimary").value,
                textSecondary = animateColorAsState(target.textSecondary, themeAnimation, label = "themeTextSecondary").value,
                textMuted = animateColorAsState(target.textMuted, themeAnimation, label = "themeTextMuted").value,
                accent = animateColorAsState(target.accent, themeAnimation, label = "themeAccent").value,
                onAccent = animateColorAsState(target.onAccent, themeAnimation, label = "themeOnAccent").value,
                accentSoft = animateColorAsState(target.accentSoft, themeAnimation, label = "themeAccentSoft").value,
                accentBorder = animateColorAsState(target.accentBorder, themeAnimation, label = "themeAccentBorder").value,
                divider = animateColorAsState(target.divider, themeAnimation, label = "themeDivider").value,
                border = animateColorAsState(target.border, themeAnimation, label = "themeBorder").value,
                active = animateColorAsState(target.active, themeAnimation, label = "themeActive").value,
                activeStrong = animateColorAsState(target.activeStrong, themeAnimation, label = "themeActiveStrong").value,
                activeSoft = animateColorAsState(target.activeSoft, themeAnimation, label = "themeActiveSoft").value,
                activeBorder = animateColorAsState(target.activeBorder, themeAnimation, label = "themeActiveBorder").value,
                error = animateColorAsState(target.error, themeAnimation, label = "themeError").value,
                errorSoft = animateColorAsState(target.errorSoft, themeAnimation, label = "themeErrorSoft").value,
            )

            LaunchedEffect(theme) {
                previousDark = theme.dark
                val style = if (theme.dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            CompositionLocalProvider(
                LocalDetourTheme provides theme,
                LocalDetourColors provides animatedColors,
            ) {
                MaterialTheme(
                    colorScheme = colorSchemeFor(animatedColors, theme.dark),
                    typography = AppTypography,
                    shapes = AppShapes,
                ) {
                    Box(Modifier.fillMaxSize().background(animatedColors.background)) {
                        val ctx = this@MainActivity
                        val navController = rememberNavController()
                        val currentEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = currentEntry?.destination?.route
                        var previousRoute by remember { mutableStateOf<String?>(null) }
                        var navMotionActive by remember { mutableStateOf(false) }

                        LaunchedEffect(currentRoute) {
                            val changed = previousRoute != null && previousRoute != currentRoute
                            previousRoute = currentRoute
                            if (changed) {
                                navMotionActive = true
                                delay(Motion.NAV_REFRESH_BOOST_MS)
                                navMotionActive = false
                            }
                        }

                        LaunchedEffect(Unit) {
                            AutoConnectCoordinator(
                                loadSettings = store::snapshot,
                                resolveRoutes = { routes ->
                                    withContext(Dispatchers.IO) {
                                        resolveEffectiveRoutes(appContext.packageManager, routes)
                                    }
                                },
                                vpnPermissionGranted = {
                                    android.net.VpnService.prepare(ctx) == null
                                },
                                currentVpnState = { VpnController.state.value },
                                startVpn = { VpnController.startNow(ctx) },
                            ).runOnce()
                        }

                        NavHost(
                            navController = navController,
                            startDestination = Route.HOME,
                            modifier = Modifier
                                .fillMaxSize()
                                .detourHighRefresh(navMotionActive),
                            enterTransition = {
                                val duration = when (targetState.destination.route) {
                                    Route.SETTINGS -> Motion.NAV_SETTINGS_ENTER_MS
                                    Route.ROUTES -> Motion.NAV_ROUTES_ENTER_MS
                                    else -> Motion.NAV_ENTER_MS
                                }
                                slideInHorizontally(
                                    animationSpec = tween(
                                        duration,
                                        easing = Motion.STANDARD_EASING,
                                    ),
                                    initialOffsetX = { it },
                                )
                            },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = {
                                val duration = when (initialState.destination.route) {
                                    Route.SETTINGS -> Motion.NAV_SETTINGS_EXIT_MS
                                    Route.ROUTES -> Motion.NAV_ROUTES_EXIT_MS
                                    else -> Motion.NAV_EXIT_MS
                                }
                                slideOutHorizontally(
                                    animationSpec = tween(
                                        duration,
                                        easing = Motion.STANDARD_EASING,
                                    ),
                                    targetOffsetX = { it },
                                )
                            },
                        ) {
                            composable(Route.HOME) {
                                val homeViewModel = viewModel<HomeViewModel>(
                                    factory = HomeViewModel.factory(
                                        store = store,
                                        resolveRoutes = { routes ->
                                            withContext(Dispatchers.IO) {
                                                resolveEffectiveRoutes(appContext.packageManager, routes)
                                            }
                                        },
                                    ),
                                )
                                HomeScreen(
                                    homeViewModel,
                                    onOpenSettings = { navController.navigate(Route.SETTINGS) },
                                )
                            }
                            composable(Route.SETTINGS) {
                                val settingsViewModel = viewModel<SettingsMenuViewModel>(
                                    factory = SettingsMenuViewModel.factory(
                                        store = store,
                                        resolveRoutes = { routes ->
                                            withContext(Dispatchers.IO) {
                                                resolveEffectiveRoutes(appContext.packageManager, routes)
                                            }
                                        },
                                    ),
                                )
                                SettingsMenuScreen(
                                    settingsViewModel,
                                    onOpenRoutes = { navController.navigate(Route.ROUTES) },
                                    onOpenVless = { navController.navigate(Route.VLESS) },
                                    onOpenDpi = { navController.navigate(Route.DPI) },
                                    onOpenTheme = { navController.navigate(Route.THEME) },
                                    onOpenDns = { navController.navigate(Route.DNS) },
                                    onOpenBackup = { navController.navigate(Route.BACKUP) },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(Route.ROUTES) {
                                val appsViewModel = viewModel<AppsViewModel>(
                                    factory = AppsViewModel.factory(
                                        store = store,
                                        initialApps = AppInventory.peek(),
                                        loadApps = {
                                            withContext(Dispatchers.IO) {
                                                AppInventory.load(appContext)
                                            }
                                        },
                                        restartTunnel = {
                                            VpnController.restartIfActive(appContext)
                                        },
                                    ),
                                )
                                AppsScreen(
                                    appsViewModel,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(Route.VLESS) {
                                val profilesViewModel = viewModel<ProfilesViewModel>(
                                    factory = ProfilesViewModel.factory(
                                        store = store,
                                        restartTunnel = {
                                            VpnController.restartIfActive(appContext)
                                        },
                                        stopTunnelIfRunning = {
                                            if (
                                                VpnController.state.value == VpnState.Active ||
                                                VpnController.state.value == VpnState.Starting
                                            ) {
                                                VpnController.stop(appContext)
                                            }
                                        },
                                    ),
                                )
                                VlessKeyScreen(
                                    profilesViewModel,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(Route.DPI) {
                                val dpiViewModel = viewModel<DpiViewModel>(
                                    factory = DpiViewModel.factory(
                                        store = store,
                                        restartTunnel = {
                                            VpnController.restartIfActive(appContext)
                                        },
                                    ),
                                )
                                DpiScreen(
                                    dpiViewModel,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(Route.THEME) {
                                ThemeScreen(store, onBack = { navController.popBackStack() })
                            }
                            composable(Route.DNS) {
                                val dnsViewModel = viewModel<DnsViewModel>(
                                    factory = DnsViewModel.factory(
                                        store = store,
                                        restartTunnel = {
                                            VpnController.restartIfActive(appContext)
                                        },
                                    ),
                                )
                                DnsScreen(
                                    dnsViewModel,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(Route.BACKUP) {
                                val backupViewModel = viewModel<BackupViewModel>(
                                    factory = BackupViewModel.factory(
                                        store = store,
                                        stopTunnelIfRunning = {
                                            if (
                                                VpnController.state.value == VpnState.Active ||
                                                VpnController.state.value == VpnState.Starting
                                            ) {
                                                VpnController.stop(appContext)
                                            }
                                        },
                                    ),
                                )
                                BackupScreen(
                                    backupViewModel,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
