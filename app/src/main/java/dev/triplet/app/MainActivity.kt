package dev.triplet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.triplet.app.ui.AppsScreen
import dev.triplet.app.ui.DpiScreen
import dev.triplet.app.ui.HomeScreen
import dev.triplet.app.ui.SettingsMenuScreen
import dev.triplet.app.ui.VlessKeyScreen

private enum class Screen { HOME, SETTINGS, ROUTES, VLESS, DPI }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as TripletApp).routesStore
        setContent {
            // Пастельная светлая палитра: белый фон слишком яркий.
            val pastel = lightColorScheme(
                primary = Color(0xFF7C6BAE),
                background = Color(0xFFEDE9F2),
                surface = Color(0xFFF8F5FB),
                surfaceVariant = Color(0xFFE7E1EE),
                onBackground = Color(0xFF2B2530),
                onSurface = Color(0xFF2B2530),
                onSurfaceVariant = Color(0xFF6E6679),
                outline = Color(0xFFB9B0C6),
            )
            MaterialTheme(colorScheme = pastel) {
                var screen by remember { mutableStateOf(Screen.HOME) }
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
                                onBack = { screen = Screen.HOME },
                                modifier,
                            )
                            Screen.ROUTES -> AppsScreen(store, onBack = { screen = Screen.SETTINGS }, modifier)
                            Screen.VLESS -> VlessKeyScreen(store, onBack = { screen = Screen.SETTINGS }, modifier)
                            Screen.DPI -> DpiScreen(store, onBack = { screen = Screen.SETTINGS }, modifier)
                        }
                    }
                }
            }
        }
    }
}
