package dev.detour.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
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
import dev.detour.app.ui.AppShapes
import dev.detour.app.ui.AppTheme
import dev.detour.app.ui.AppTypography
import dev.detour.app.ui.DetourColors
import dev.detour.app.ui.LocalDetourColors
import dev.detour.app.ui.LocalDetourTheme
import dev.detour.app.ui.Motion
import dev.detour.app.ui.colorSchemeFor
import dev.detour.app.ui.configureAdaptiveRefresh
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun themeTransitionDuration(previousDark: Boolean, targetDark: Boolean): Int =
    if (previousDark == targetDark) Motion.THEME_MS else 0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureAdaptiveRefresh(window)

        val store = (application as DetourApp).routesStore
        val appContext = applicationContext
        val initialTheme = AppTheme.byId(store.settings.value?.themeId.orEmpty())

        setContent {
            val themeFlow = remember(store) {
                store.settings
                    .map { AppTheme.byId(it?.themeId.orEmpty()) }
                    .distinctUntilChanged()
            }
            val theme by themeFlow.collectAsStateWithLifecycle(initialValue = initialTheme)
            val target = theme.colors
            var previousDark by remember { mutableStateOf(theme.dark) }
            val themeAnimation = tween<Color>(themeTransitionDuration(previousDark, theme.dark))

            // Interpolate within the same brightness mode. A continuous light↔dark
            // palette interpolation necessarily crosses a frame where foreground and
            // background luminance converge, so cross-mode changes snap atomically.
            val animatedColors = DetourColors(
                background = animateColorAsState(
                    target.background,
                    themeAnimation,
                    label = "themeBackground",
                ).value,
                surface = animateColorAsState(
                    target.surface,
                    themeAnimation,
                    label = "themeSurface",
                ).value,
                surfaceSoft = animateColorAsState(
                    target.surfaceSoft,
                    themeAnimation,
                    label = "themeSurfaceSoft",
                ).value,
                surfaceSelected = animateColorAsState(
                    target.surfaceSelected,
                    themeAnimation,
                    label = "themeSelected",
                ).value,
                textPrimary = animateColorAsState(
                    target.textPrimary,
                    themeAnimation,
                    label = "themeTextPrimary",
                ).value,
                textSecondary = animateColorAsState(
                    target.textSecondary,
                    themeAnimation,
                    label = "themeTextSecondary",
                ).value,
                textMuted = animateColorAsState(
                    target.textMuted,
                    themeAnimation,
                    label = "themeTextMuted",
                ).value,
                accent = animateColorAsState(
                    target.accent,
                    themeAnimation,
                    label = "themeAccent",
                ).value,
                onAccent = animateColorAsState(
                    target.onAccent,
                    themeAnimation,
                    label = "themeOnAccent",
                ).value,
                accentSoft = animateColorAsState(
                    target.accentSoft,
                    themeAnimation,
                    label = "themeAccentSoft",
                ).value,
                accentBorder = animateColorAsState(
                    target.accentBorder,
                    themeAnimation,
                    label = "themeAccentBorder",
                ).value,
                divider = animateColorAsState(
                    target.divider,
                    themeAnimation,
                    label = "themeDivider",
                ).value,
                border = animateColorAsState(
                    target.border,
                    themeAnimation,
                    label = "themeBorder",
                ).value,
                active = animateColorAsState(
                    target.active,
                    themeAnimation,
                    label = "themeActive",
                ).value,
                activeStrong = animateColorAsState(
                    target.activeStrong,
                    themeAnimation,
                    label = "themeActiveStrong",
                ).value,
                activeSoft = animateColorAsState(
                    target.activeSoft,
                    themeAnimation,
                    label = "themeActiveSoft",
                ).value,
                activeBorder = animateColorAsState(
                    target.activeBorder,
                    themeAnimation,
                    label = "themeActiveBorder",
                ).value,
                error = animateColorAsState(
                    target.error,
                    themeAnimation,
                    label = "themeError",
                ).value,
                errorSoft = animateColorAsState(
                    target.errorSoft,
                    themeAnimation,
                    label = "themeErrorSoft",
                ).value,
            )

            LaunchedEffect(theme) {
                previousDark = theme.dark
                val style = if (theme.dark) {
                    SystemBarStyle.dark(Color.Transparent.toArgb())
                } else {
                    SystemBarStyle.light(
                        Color.Transparent.toArgb(),
                        Color.Transparent.toArgb(),
                    )
                }
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
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(animatedColors.background),
                    ) {
                        DetourNavigation(
                            store = store,
                            appContext = appContext,
                        )
                    }
                }
            }
        }
    }
}
