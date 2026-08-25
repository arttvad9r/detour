package dev.triplet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.triplet.app.ui.AppsScreen
import dev.triplet.app.ui.AppShapes
import dev.triplet.app.ui.AppTheme
import dev.triplet.app.ui.AppTypography
import dev.triplet.app.ui.BackupScreen
import dev.triplet.app.ui.DnsScreen
import dev.triplet.app.ui.DpiScreen
import dev.triplet.app.ui.HomeScreen
import dev.triplet.app.ui.SettingsMenuScreen
import dev.triplet.app.ui.ThemeScreen
import dev.triplet.app.vpn.VpnController
import dev.triplet.app.vpn.VpnState
import dev.triplet.app.ui.VlessKeyScreen

private enum class Screen { HOME, SETTINGS, ROUTES, VLESS, DPI, THEME, DNS, BACKUP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as TripletApp).routesStore
        setContent {
            val settings by store.settings.collectAsState(initial = null)
            val theme = AppTheme.byId(settings?.themeId ?: "")
            MaterialTheme(colorScheme = theme.scheme, typography = AppTypography, shapes = AppShapes) {
                // Иконки системных баров под активную тему (тёмные темы — светлые иконки).
                val darkTheme = theme == AppTheme.MIDNIGHT || theme == AppTheme.GRAPHITE
                LaunchedEffect(darkTheme) {
                    val style = if (darkTheme) SystemBarStyle.dark(Color.Transparent.toArgb())
                    else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                var screen by remember { mutableStateOf(Screen.HOME) }
                // Автоподключение при старте (настройка, по умолчанию выключена).
                val ctx = this
                LaunchedEffect(settings?.autoConnect) {
                    if (settings?.autoConnect == true &&
                        VpnController.state.value == VpnState.Idle &&
                        !settings?.vlessUri.isNullOrBlank()
                    ) {
                        dev.triplet.app.vpn.VpnController.start(ctx, { })
                    }
                }
                // Назад: с подстраниц — в меню настроек, из настроек — на главную.
                BackHandler(enabled = screen != Screen.HOME) {
                    screen = if (screen == Screen.SETTINGS) Screen.HOME else Screen.SETTINGS
                }
                Scaffold { pad ->
                    Crossfade(targetState = screen, label = "screen") { s ->
                        val modifier = Modifier.padding(pad)
                        when (s) {
                            Screen.HOME -> HomeScreen(store, onOpenSettings = { screen = Screen.SETTINGS }, modifier)
                            Screen.SETTINGS -> SettingsMenuScreen(
                                store,
                                onOpenRoutes = { screen = Screen.ROUTES },
                                onOpenVless = { screen = Screen.VLESS },
                                onOpenDpi = { screen = Screen.DPI },
                                onOpenTheme = { screen = Screen.THEME },
                                onOpenDns = { screen = Screen.DNS },
                                onOpenBackup = { screen = Screen.BACKUP },
                                onBack = { screen = Screen.HOME },
                                modifier,
                            )
                            Screen.ROUTES -> AppsScreen(store, onBack = { screen = Screen.SETTINGS }, modifier)
                            Screen.VLESS -> VlessKeyScreen(store, onBack = { screen = Screen.SETTINGS }, modifier)
                            Screen.DPI -> DpiScreen(store, onBack = { screen = Screen.SETTINGS }, modifier)
                            Screen.THEME -> ThemeScreen(store, onBack = { screen = Screen.SETTINGS }, modifier)
                            Screen.DNS -> DnsScreen(store, onBack = { screen = Screen.SETTINGS }, modifier)
                            Screen.BACKUP -> BackupScreen(store, onBack = { screen = Screen.SETTINGS }, modifier)
                        }
                    }
                }
            }
        }
    }
}
