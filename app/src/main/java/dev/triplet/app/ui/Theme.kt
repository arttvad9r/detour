@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.triplet.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R

/** Semantic colors consumed by screens and shared components. */
data class DetourColors(
    val background: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val surfaceSelected: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentBorder: Color,
    val divider: Color,
    val border: Color,
    val active: Color,
    val activeStrong: Color,
    val activeSoft: Color,
    val activeBorder: Color,
    val error: Color,
    val errorSoft: Color,
)

data class StatusStyle(val container: Color, val content: Color, val border: Color)

enum class AppTheme(
    val id: String,
    val label: String,
    val colors: DetourColors,
    val dark: Boolean,
) {
    CATPPUCCIN_LATTE(
        "catppuccin_latte", "Catppuccin Latte",
        DetourColors(
            background = Color(0xFFEFF1F5),
            surface = Color(0xFFE6E9EF),
            surfaceSoft = Color(0xFFDCE0E8),
            surfaceSelected = Color(0xFFCCD0DA),
            textPrimary = Color(0xFF4C4F69),
            textSecondary = Color(0xFF595C74),
            textMuted = Color(0xFF64677A),
            accent = Color(0xFF8839EF),
            onAccent = Color(0xFFFFFFFF),
            accentSoft = Color(0xFF8839EF).copy(alpha = 0.12f),
            accentBorder = Color(0xFF7287FD),
            divider = Color(0xFFCCD0DA),
            border = Color(0xFFBCC0CC),
            active = Color(0xFF40A02B),
            activeStrong = Color(0xFF40A02B),
            activeSoft = Color(0xFF40A02B).copy(alpha = 0.12f),
            activeBorder = Color(0xFF40A02B).copy(alpha = 0.42f),
            error = Color(0xFFD20F39),
            errorSoft = Color(0xFFD20F39).copy(alpha = 0.10f),
        ),
        dark = false,
    ),
    CATPPUCCIN_MOCHA(
        "catppuccin_mocha", "Catppuccin Mocha",
        DetourColors(
            background = Color(0xFF1E1E2E),
            surface = Color(0xFF313244),
            surfaceSoft = Color(0xFF181825),
            surfaceSelected = Color(0xFF45475A),
            textPrimary = Color(0xFFCDD6F4),
            textSecondary = Color(0xFFBAC2DE),
            textMuted = Color(0xFF9AA1BB),
            accent = Color(0xFFCBA6F7),
            onAccent = Color(0xFF1E1E2E),
            accentSoft = Color(0xFFCBA6F7).copy(alpha = 0.16f),
            accentBorder = Color(0xFFB4BEFE),
            divider = Color(0xFF45475A),
            border = Color(0xFF585B70),
            active = Color(0xFFA6E3A1),
            activeStrong = Color(0xFFA6E3A1),
            activeSoft = Color(0xFFA6E3A1).copy(alpha = 0.13f),
            activeBorder = Color(0xFFA6E3A1).copy(alpha = 0.42f),
            error = Color(0xFFF38BA8),
            errorSoft = Color(0xFFF38BA8).copy(alpha = 0.13f),
        ),
        dark = true,
    ),
    GRUVBOX_DARK(
        "gruvbox_dark", "Gruvbox Dark",
        DetourColors(
            background = Color(0xFF282828),
            surface = Color(0xFF3C3836),
            surfaceSoft = Color(0xFF32302F),
            surfaceSelected = Color(0xFF504945),
            textPrimary = Color(0xFFEBDBB2),
            textSecondary = Color(0xFFD5C4A1),
            textMuted = Color(0xFFBDAE93),
            accent = Color(0xFFFE8019),
            onAccent = Color(0xFF282828),
            accentSoft = Color(0xFFFE8019).copy(alpha = 0.14f),
            accentBorder = Color(0xFFD79921),
            divider = Color(0xFF504945),
            border = Color(0xFF665C54),
            active = Color(0xFFB8BB26),
            activeStrong = Color(0xFFB8BB26),
            activeSoft = Color(0xFFB8BB26).copy(alpha = 0.13f),
            activeBorder = Color(0xFF98971A),
            error = Color(0xFFFF5540),
            errorSoft = Color(0xFFFF5540).copy(alpha = 0.12f),
        ),
        dark = true,
    ),
    DRACULA(
        "dracula", "Dracula",
        DetourColors(
            background = Color(0xFF282A36),
            surface = Color(0xFF343746),
            surfaceSoft = Color(0xFF21222C),
            surfaceSelected = Color(0xFF44475A),
            textPrimary = Color(0xFFF8F8F2),
            textSecondary = Color(0xFFF8F8F2).copy(alpha = 0.72f),
            textMuted = Color(0xFFA3A5B0),
            accent = Color(0xFFBD93F9),
            onAccent = Color(0xFF282A36),
            accentSoft = Color(0xFFBD93F9).copy(alpha = 0.16f),
            accentBorder = Color(0xFF6272A4),
            divider = Color(0xFF44475A),
            border = Color(0xFF6272A4).copy(alpha = 0.70f),
            active = Color(0xFF50FA7B),
            activeStrong = Color(0xFF50FA7B),
            activeSoft = Color(0xFF50FA7B).copy(alpha = 0.12f),
            activeBorder = Color(0xFF50FA7B).copy(alpha = 0.40f),
            error = Color(0xFFFF5555),
            errorSoft = Color(0xFFFF5555).copy(alpha = 0.12f),
        ),
        dark = true,
    );

    val scheme: ColorScheme by lazy { colorSchemeFor(colors, dark) }

    fun statusFor(state: dev.triplet.app.vpn.VpnState): StatusStyle = statusStyleFor(colors, state)

    companion object {
        /** Preserve settings and exported backups from the original palette set. */
        fun byId(id: String): AppTheme = entries.firstOrNull { it.id == id } ?: when (id) {
            "lavenda", "ocean" -> CATPPUCCIN_LATTE
            "midnight" -> CATPPUCCIN_MOCHA
            "graphite" -> GRUVBOX_DARK
            else -> CATPPUCCIN_LATTE
        }
    }
}

fun colorSchemeFor(c: DetourColors, dark: Boolean): ColorScheme {
    val light = lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
        secondaryContainer = c.surfaceSelected, onSecondaryContainer = c.textPrimary,
        background = c.background, onBackground = c.textPrimary,
        surface = c.surface, onSurface = c.textPrimary,
        surfaceVariant = c.surfaceSoft, onSurfaceVariant = c.textSecondary,
        outline = c.border, outlineVariant = c.divider,
        error = c.error,
    )
    return if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
        secondaryContainer = c.surfaceSelected, onSecondaryContainer = c.textPrimary,
        background = c.background, onBackground = c.textPrimary,
        surface = c.surface, onSurface = c.textPrimary,
        surfaceVariant = c.surfaceSoft, onSurfaceVariant = c.textSecondary,
        outline = c.border, outlineVariant = c.divider,
        error = c.error,
    ) else light
}

/** Keep card fills neutral; state is communicated through accent/error details. */
fun statusStyleFor(colors: DetourColors, state: dev.triplet.app.vpn.VpnState): StatusStyle = when (state) {
    dev.triplet.app.vpn.VpnState.Active -> StatusStyle(colors.surfaceSelected, colors.accent, colors.accentBorder)
    dev.triplet.app.vpn.VpnState.Starting -> StatusStyle(colors.surface, colors.accent, colors.accentBorder)
    is dev.triplet.app.vpn.VpnState.Failed -> StatusStyle(colors.surface, colors.error, colors.error.copy(alpha = .45f))
    dev.triplet.app.vpn.VpnState.Idle -> StatusStyle(colors.surface, colors.textPrimary, colors.border)
}

fun themeLabel(theme: AppTheme): Int = when (theme) {
    AppTheme.CATPPUCCIN_LATTE -> R.string.theme_catppuccin_latte
    AppTheme.CATPPUCCIN_MOCHA -> R.string.theme_catppuccin_mocha
    AppTheme.GRUVBOX_DARK -> R.string.theme_gruvbox_dark
    AppTheme.DRACULA -> R.string.theme_dracula
}

/** Target theme metadata (id/name/dark). */
val LocalDetourTheme = androidx.compose.runtime.staticCompositionLocalOf<AppTheme> { AppTheme.CATPPUCCIN_LATTE }

/** Animated semantic colors used by the rendered UI. */
val LocalDetourColors = androidx.compose.runtime.staticCompositionLocalOf<DetourColors> {
    AppTheme.CATPPUCCIN_LATTE.colors
}

object Spacing {
    val space2 = 2.dp
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space40 = 40.dp
    val space48 = 48.dp
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
)
val PillShape = RoundedCornerShape(999.dp)

private fun inter(weight: FontWeight, w: Int) = Font(
    R.font.inter_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val AppFontFamily = FontFamily(
    inter(FontWeight.Normal, 400),
    inter(FontWeight.Medium, 500),
    inter(FontWeight.SemiBold, 600),
)

val AppTypography: Typography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp, fontFamily = AppFontFamily),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp, fontFamily = AppFontFamily),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, letterSpacing = 0.2.sp, fontFamily = AppFontFamily),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, fontFamily = AppFontFamily),
    bodyLarge = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, fontFamily = AppFontFamily),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp, fontFamily = AppFontFamily),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, fontFamily = AppFontFamily),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = AppFontFamily),
    labelMedium = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, fontFamily = AppFontFamily),
    labelSmall = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Normal, lineHeight = 15.sp, fontFamily = AppFontFamily),
)
