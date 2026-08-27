package dev.triplet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.triplet.app.ui.AppShapes
import dev.triplet.app.ui.AppTheme
import dev.triplet.app.ui.AppTypography
import dev.triplet.app.ui.AppsScreen
import dev.triplet.app.ui.BackupScreen
import dev.triplet.app.ui.DnsScreen
import dev.triplet.app.ui.DpiScreen
import dev.triplet.app.ui.HomeScreen
import dev.triplet.app.ui.LocalDetourTheme
import dev.triplet.app.ui.SettingsMenuScreen
import dev.triplet.app.ui.ThemeScreen
import dev.triplet.app.ui.VlessKeyScreen
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import dev.triplet.app.vpn.canAutoConnect
import dev.triplet.app.vpn.effectiveRoutes
import dev.triplet.app.core.ParseResult
import dev.triplet.app.core.VlessKeyParser

private enum class Screen { HOME, SETTINGS, ROUTES, VLESS, DPI, THEME, DNS, BACKUP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as TripletApp).routesStore
        setContent {
            val settings by store.settings.collectAsState(initial = null)
            val theme = AppTheme.byId(settings?.themeId ?: "")
            LaunchedEffect(theme) {
                val style = if (theme.dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CompositionLocalProvider(LocalDetourTheme provides theme) {
                MaterialTheme(colorScheme = theme.scheme, typography = AppTypography, shapes = AppShapes) {
                    Box(Modifier.fillMaxSize().background(theme.colors.background)) {
                        var screen by rememberSaveable { androidx.compose.runtime.mutableStateOf(Screen.HOME) }
                        val ctx = this@MainActivity
                        LaunchedEffect(settings?.autoConnect, settings?.routes, settings?.vlessUri) {
                            val effective = settings?.let { s ->
                                effectiveRoutes(s.routes, s.routes.keys.associateWith { pkg ->
                                    runCatching { packageManager.getPackageUid(pkg, 0) }.getOrNull()
                                })
                            }
                            val activeValid = settings?.vlessKeys?.active?.uri?.let {
                                VlessKeyParser.parse(it) is ParseResult.Ok
                            } == true
                            if (settings != null && effective != null &&
                                canAutoConnect(settings!!, android.net.VpnService.prepare(ctx) == null, effective, activeValid) &&
                                VpnController.state.value == VpnState.Idle
                            ) {
                                VpnController.startNow(ctx)
                            }
                        }
                        BackHandler(enabled = screen != Screen.HOME) {
                            screen = if (screen == Screen.SETTINGS) Screen.HOME else Screen.SETTINGS
                        }
                        Crossfade(
                            targetState = screen,
                            animationSpec = tween(durationMillis = 160),
                            label = "screen",
                        ) { s ->
                            when (s) {
                                Screen.HOME -> HomeScreen(store, onOpenSettings = { screen = Screen.SETTINGS })
                                Screen.SETTINGS -> SettingsMenuScreen(
                                    store,
                                    onOpenRoutes = { screen = Screen.ROUTES },
                                    onOpenVless = { screen = Screen.VLESS },
                                    onOpenDpi = { screen = Screen.DPI },
                                    onOpenTheme = { screen = Screen.THEME },
                                    onOpenDns = { screen = Screen.DNS },
                                    onOpenBackup = { screen = Screen.BACKUP },
                                    onBack = { screen = Screen.HOME },
                                )
                                Screen.ROUTES -> AppsScreen(store, onBack = { screen = Screen.SETTINGS })
                                Screen.VLESS -> VlessKeyScreen(store, onBack = { screen = Screen.SETTINGS })
                                Screen.DPI -> DpiScreen(store, onBack = { screen = Screen.SETTINGS })
                                Screen.THEME -> ThemeScreen(store, onBack = { screen = Screen.SETTINGS })
                                Screen.DNS -> DnsScreen(store, onBack = { screen = Screen.SETTINGS })
                                Screen.BACKUP -> BackupScreen(store, onBack = { screen = Screen.SETTINGS })
                            }
                        }
                    }
                }
            }
        }
    }
}
