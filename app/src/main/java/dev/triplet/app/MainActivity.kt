package dev.triplet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.ui.AppShapes
import dev.triplet.app.ui.AppTheme
import dev.triplet.app.ui.AppTypography
import dev.triplet.app.ui.AppsScreen
import dev.triplet.app.ui.BackupScreen
import dev.triplet.app.ui.DetourColors
import dev.triplet.app.ui.DnsScreen
import dev.triplet.app.ui.DpiScreen
import dev.triplet.app.ui.HomeScreen
import dev.triplet.app.ui.LocalDetourColors
import dev.triplet.app.ui.LocalDetourTheme
import dev.triplet.app.ui.Motion
import dev.triplet.app.ui.SettingsMenuScreen
import dev.triplet.app.ui.ThemeScreen
import dev.triplet.app.ui.VlessKeyScreen
import dev.triplet.app.ui.colorSchemeFor
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import dev.triplet.app.vpn.canAutoConnect
import dev.triplet.app.vpn.resolveEffectiveRoutes
import kotlinx.coroutines.Dispatchers
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as TripletApp).routesStore
        setContent {
            val settings by store.settings.collectAsState()
            val theme = AppTheme.byId(settings?.themeId ?: "")
            val target = theme.colors

            // Theme changes interpolate semantic colors in place instead of flashing
            // the whole composition or creating a second navigation tree.
            val animatedColors = DetourColors(
                background = animateColorAsState(target.background, tween(Motion.THEME_MS), label = "themeBackground").value,
                surface = animateColorAsState(target.surface, tween(Motion.THEME_MS), label = "themeSurface").value,
                surfaceSoft = animateColorAsState(target.surfaceSoft, tween(Motion.THEME_MS), label = "themeSurfaceSoft").value,
                surfaceSelected = animateColorAsState(target.surfaceSelected, tween(Motion.THEME_MS), label = "themeSelected").value,
                textPrimary = animateColorAsState(target.textPrimary, tween(Motion.THEME_MS), label = "themeTextPrimary").value,
                textSecondary = animateColorAsState(target.textSecondary, tween(Motion.THEME_MS), label = "themeTextSecondary").value,
                textMuted = animateColorAsState(target.textMuted, tween(Motion.THEME_MS), label = "themeTextMuted").value,
                accent = animateColorAsState(target.accent, tween(Motion.THEME_MS), label = "themeAccent").value,
                onAccent = animateColorAsState(target.onAccent, tween(Motion.THEME_MS), label = "themeOnAccent").value,
                accentSoft = animateColorAsState(target.accentSoft, tween(Motion.THEME_MS), label = "themeAccentSoft").value,
                accentBorder = animateColorAsState(target.accentBorder, tween(Motion.THEME_MS), label = "themeAccentBorder").value,
                divider = animateColorAsState(target.divider, tween(Motion.THEME_MS), label = "themeDivider").value,
                border = animateColorAsState(target.border, tween(Motion.THEME_MS), label = "themeBorder").value,
                active = animateColorAsState(target.active, tween(Motion.THEME_MS), label = "themeActive").value,
                activeStrong = animateColorAsState(target.activeStrong, tween(Motion.THEME_MS), label = "themeActiveStrong").value,
                activeSoft = animateColorAsState(target.activeSoft, tween(Motion.THEME_MS), label = "themeActiveSoft").value,
                activeBorder = animateColorAsState(target.activeBorder, tween(Motion.THEME_MS), label = "themeActiveBorder").value,
                error = animateColorAsState(target.error, tween(Motion.THEME_MS), label = "themeError").value,
                errorSoft = animateColorAsState(target.errorSoft, tween(Motion.THEME_MS), label = "themeErrorSoft").value,
            )

            LaunchedEffect(theme) {
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

                        LaunchedEffect(Unit) {
                            val launchSettings = store.snapshot()
                            val effective = withContext(Dispatchers.IO) {
                                resolveEffectiveRoutes(packageManager, launchSettings.routes)
                            }
                            val activeVpnValid = when (launchSettings.activeVpn) {
                                VpnProfileKind.VLESS -> launchSettings.vlessKeys.active?.uri?.let {
                                    VlessKeyParser.parse(it) is ParseResult.Ok
                                } == true
                                VpnProfileKind.WARP -> launchSettings.warpProfile != null
                            }
                            if (
                                canAutoConnect(
                                    launchSettings,
                                    android.net.VpnService.prepare(ctx) == null,
                                    effective,
                                    activeVpnValid,
                                ) && VpnController.state.value == VpnState.Idle
                            ) {
                                VpnController.startNow(ctx)
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = Route.HOME,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                val rootTransition =
                                    initialState.destination.route == Route.HOME &&
                                        targetState.destination.route == Route.SETTINGS
                                if (rootTransition) {
                                    fadeIn(
                                        tween(
                                            Motion.NAV_ENTER_MS,
                                            delayMillis = 20,
                                            easing = Motion.ENTER_EASING,
                                        ),
                                    ) + scaleIn(
                                        tween(Motion.NAV_ENTER_MS, easing = Motion.ENTER_EASING),
                                        initialScale = 0.985f,
                                    )
                                } else {
                                    fadeIn(
                                        tween(
                                            Motion.NAV_ENTER_MS,
                                            delayMillis = 24,
                                            easing = Motion.ENTER_EASING,
                                        ),
                                    ) + slideInHorizontally(
                                        animationSpec = tween(
                                            Motion.NAV_ENTER_MS,
                                            easing = Motion.ENTER_EASING,
                                        ),
                                        initialOffsetX = { it / 12 },
                                    )
                                }
                            },
                            exitTransition = {
                                val rootTransition =
                                    initialState.destination.route == Route.HOME &&
                                        targetState.destination.route == Route.SETTINGS
                                if (rootTransition) {
                                    fadeOut(
                                        tween(Motion.NAV_EXIT_MS, easing = Motion.EXIT_EASING),
                                    ) + scaleOut(
                                        tween(Motion.NAV_EXIT_MS, easing = Motion.EXIT_EASING),
                                        targetScale = 0.995f,
                                    )
                                } else {
                                    fadeOut(
                                        tween(Motion.NAV_EXIT_MS, easing = Motion.EXIT_EASING),
                                    ) + slideOutHorizontally(
                                        animationSpec = tween(
                                            Motion.NAV_EXIT_MS,
                                            easing = Motion.EXIT_EASING,
                                        ),
                                        targetOffsetX = { -it / 36 },
                                    )
                                }
                            },
                            popEnterTransition = {
                                val rootTransition =
                                    initialState.destination.route == Route.SETTINGS &&
                                        targetState.destination.route == Route.HOME
                                if (rootTransition) {
                                    fadeIn(
                                        tween(
                                            Motion.NAV_ENTER_MS,
                                            delayMillis = 12,
                                            easing = Motion.ENTER_EASING,
                                        ),
                                    ) + scaleIn(
                                        tween(Motion.NAV_ENTER_MS, easing = Motion.ENTER_EASING),
                                        initialScale = 0.992f,
                                    )
                                } else {
                                    fadeIn(
                                        tween(
                                            Motion.NAV_ENTER_MS,
                                            delayMillis = 16,
                                            easing = Motion.ENTER_EASING,
                                        ),
                                    ) + slideInHorizontally(
                                        animationSpec = tween(
                                            Motion.NAV_ENTER_MS,
                                            easing = Motion.ENTER_EASING,
                                        ),
                                        initialOffsetX = { -it / 36 },
                                    )
                                }
                            },
                            popExitTransition = {
                                val rootTransition =
                                    initialState.destination.route == Route.SETTINGS &&
                                        targetState.destination.route == Route.HOME
                                if (rootTransition) {
                                    fadeOut(
                                        tween(Motion.NAV_EXIT_MS, easing = Motion.EXIT_EASING),
                                    ) + scaleOut(
                                        tween(Motion.NAV_EXIT_MS, easing = Motion.EXIT_EASING),
                                        targetScale = 0.985f,
                                    )
                                } else {
                                    fadeOut(
                                        tween(Motion.NAV_EXIT_MS, easing = Motion.EXIT_EASING),
                                    ) + slideOutHorizontally(
                                        animationSpec = tween(
                                            Motion.NAV_EXIT_MS,
                                            easing = Motion.EXIT_EASING,
                                        ),
                                        targetOffsetX = { it / 12 },
                                    )
                                }
                            },
                        ) {
                            composable(Route.HOME) {
                                HomeScreen(
                                    store,
                                    onOpenSettings = { navController.navigate(Route.SETTINGS) },
                                )
                            }
                            composable(Route.SETTINGS) {
                                SettingsMenuScreen(
                                    store,
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
                                AppsScreen(store, onBack = { navController.popBackStack() })
                            }
                            composable(Route.VLESS) {
                                VlessKeyScreen(store, onBack = { navController.popBackStack() })
                            }
                            composable(Route.DPI) {
                                DpiScreen(store, onBack = { navController.popBackStack() })
                            }
                            composable(Route.THEME) {
                                ThemeScreen(store, onBack = { navController.popBackStack() })
                            }
                            composable(Route.DNS) {
                                DnsScreen(store, onBack = { navController.popBackStack() })
                            }
                            composable(Route.BACKUP) {
                                BackupScreen(store, onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}
