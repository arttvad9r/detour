@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.triplet.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import dev.triplet.app.R
import dev.triplet.app.vpn.VpnState

/**
 * Четыре темы приложения. Полночь/Графит — тёмные, Океан/Лаванда — светлые.
 * Статусные цвета (карточка состояния) подобраны под каждую тему отдельно.
 */
enum class AppTheme(
    val id: String,
    val label: String,
    val scheme: ColorScheme,
    val statusOn: Pair<Color, Color>,
    val statusStarting: Pair<Color, Color>,
    val statusFailed: Pair<Color, Color>,
    val statusIdle: Pair<Color, Color>,
) {
    MIDNIGHT(
        "midnight", "Полночь",
        darkColorScheme(
            primary = Color(0xFF22C55E), onPrimary = Color(0xFF0F172A),
            background = Color(0xFF0F172A), onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF192134), onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF1E293B), onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF334155),
        ),
        Color(0xFF16351F) to Color(0xFF22C55E),
        Color(0xFF16283F) to Color(0xFF60A5FA),
        Color(0xFF3B1A1A) to Color(0xFFF87171),
        Color(0xFF1E293B) to Color(0xFF94A3B8),
    ),
    OCEAN(
        "ocean", "Океан",
        lightColorScheme(
            primary = Color(0xFF0369A1), onPrimary = Color(0xFFFFFFFF),
            background = Color(0xFFF0F6FC), onBackground = Color(0xFF0C4A6E),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0C4A6E),
            surfaceVariant = Color(0xFFE0EDF7), onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFB9D4E8),
        ),
        Color(0xFFDCF5E7) to Color(0xFF16A34A),
        Color(0xFFE0EDF7) to Color(0xFF0369A1),
        Color(0xFFFCE4E4) to Color(0xFFDC2626),
        Color(0xFFE7EFF5) to Color(0xFF64748B),
    ),
    GRAPHITE(
        "graphite", "Графит и янтарь",
        darkColorScheme(
            primary = Color(0xFFF59E0B), onPrimary = Color(0xFF121212),
            background = Color(0xFF121212), onBackground = Color(0xFFE8E8E8),
            surface = Color(0xFF1E1E1E), onSurface = Color(0xFFE8E8E8),
            surfaceVariant = Color(0xFF232323), onSurfaceVariant = Color(0xFF9E9E9E),
            outline = Color(0xFF3A3A3A),
        ),
        Color(0xFF2A2113) to Color(0xFFFBBF24),
        Color(0xFF1F2430) to Color(0xFF93B4F8),
        Color(0xFF331B1B) to Color(0xFFF87171),
        Color(0xFF232323) to Color(0xFF9E9E9E),
    ),
    LAVENDA(
        "lavenda", "Лаванда",
        lightColorScheme(
            primary = Color(0xFF6D5BA6), onPrimary = Color(0xFFFFFFFF),
            background = Color(0xFFE9E2F2), onBackground = Color(0xFF2F2A3D),
            surface = Color(0xFFF6F2FB), onSurface = Color(0xFF2F2A3D),
            surfaceVariant = Color(0xFFE2D9EF), onSurfaceVariant = Color(0xFF6E6679),
            outline = Color(0xFFC4B9D8),
        ),
        Color(0xFFDDF2E6) to Color(0xFF34A86B),
        Color(0xFFE4E0F5) to Color(0xFF6D5BA6),
        Color(0xFFF9E4E4) to Color(0xFFC94444),
        Color(0xFFE4DFEC) to Color(0xFF6E6679),
    );

    companion object {
        fun byId(id: String): AppTheme = entries.firstOrNull { it.id == id } ?: LAVENDA
    }
}

/** Цвета статусной карточки под выбранную тему. */
data class StatusStyle(val container: Color, val content: Color)

fun AppTheme.statusFor(state: VpnState): StatusStyle = when (state) {
    VpnState.Active -> StatusStyle(statusOn.first, statusOn.second)
    VpnState.Starting -> StatusStyle(statusStarting.first, statusStarting.second)
    is VpnState.Failed -> StatusStyle(statusFailed.first, statusFailed.second)
    VpnState.Idle -> StatusStyle(statusIdle.first, statusIdle.second)
}

private fun inter(weight: FontWeight, w: Int) = Font(
    R.font.inter_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

/** Inter (variable) — популярный шрифт с полной кириллицей. */
val AppFontFamily = FontFamily(
    inter(FontWeight.Normal, 400),
    inter(FontWeight.Medium, 500),
    inter(FontWeight.SemiBold, 600),
    inter(FontWeight.Bold, 700),
    inter(FontWeight.ExtraBold, 800),
)

val AppTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = AppFontFamily),
        displayMedium = t.displayMedium.copy(fontFamily = AppFontFamily),
        displaySmall = t.displaySmall.copy(fontFamily = AppFontFamily),
        headlineLarge = t.headlineLarge.copy(fontFamily = AppFontFamily),
        headlineMedium = t.headlineMedium.copy(fontFamily = AppFontFamily),
        headlineSmall = t.headlineSmall.copy(fontFamily = AppFontFamily),
        titleLarge = t.titleLarge.copy(fontFamily = AppFontFamily),
        titleMedium = t.titleMedium.copy(fontFamily = AppFontFamily),
        titleSmall = t.titleSmall.copy(fontFamily = AppFontFamily),
        bodyLarge = t.bodyLarge.copy(fontFamily = AppFontFamily),
        bodyMedium = t.bodyMedium.copy(fontFamily = AppFontFamily),
        bodySmall = t.bodySmall.copy(fontFamily = AppFontFamily),
        labelLarge = t.labelLarge.copy(fontFamily = AppFontFamily),
        labelMedium = t.labelMedium.copy(fontFamily = AppFontFamily),
        labelSmall = t.labelSmall.copy(fontFamily = AppFontFamily),
    )
}
