@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.triplet.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triplet.app.R

/**
 * Семантические токены оформления. Экраны ссылаются только на эти поля —
 * смена темы обновляет всё приложение системно. Сырые Color(0xFF…) живут
 * только здесь.
 */
data class DetourColors(
    // Уровень 1: фон приложения
    val background: Color,
    // Уровень 2: функциональные поверхности
    val surface: Color,
    val surfaceSoft: Color,
    val surfaceSelected: Color,
    // Текст
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // Уровень 3: лавандовый акцент (выбор, primary action, фокус)
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentBorder: Color,
    // Разделители и границы
    val divider: Color,
    val border: Color,
    // Зелёный — только успешный VPN; красный — только ошибки
    val active: Color,
    val activeStrong: Color,
    val activeSoft: Color,
    val activeBorder: Color,
    val error: Color,
    val errorSoft: Color,
    // Горный пейзаж: базовый тон слоями от дальнего к переднему + туман
)

/** Цвета статусной карточки/кнопки под состояние туннеля. */
data class StatusStyle(val container: Color, val content: Color, val border: Color)

/**
 * Четыре темы. Полночь/Графит — тёмные, Океан/Лаванда — светлые.
 */
enum class AppTheme(
    val id: String,
    val label: String,
    val colors: DetourColors,
    val dark: Boolean,
) {
    MIDNIGHT(
        "midnight", "Midnight",
        DetourColors(
            background = Color(0xFF0F172A),
            surface = Color(0xFF182238), surfaceSoft = Color(0xFF1B2740), surfaceSelected = Color(0xFF24304E),
            textPrimary = Color(0xFFF1F5F9), textSecondary = Color(0xFF94A3B8), textMuted = Color(0xFF64748B),
            accent = Color(0xFF60A5FA), onAccent = Color(0xFF0B1526),
            accentSoft = Color(0xFF1B2A4A), accentBorder = Color(0xFF33507E),
            divider = Color(0xFF223052), border = Color(0xFF2A3A5C),
            active = Color(0xFF34B377), activeStrong = Color(0xFF4CC78C),
            activeSoft = Color(0xFF14301F), activeBorder = Color(0xFF245A3C),
            error = Color(0xFFF87171), errorSoft = Color(0xFF3B1A1A),
        ),
        dark = true,
    ),
    OCEAN(
        "ocean", "Ocean",
        DetourColors(
            background = Color(0xFFF0F6FC),
            surface = Color(0xFFFBFDFF), surfaceSoft = Color(0xFFF3F8FC), surfaceSelected = Color(0xFFE3EEF7),
            textPrimary = Color(0xFF0C2A3E), textSecondary = Color(0xFF48657B), textMuted = Color(0xFF7A93A5),
            accent = Color(0xFF0369A1), onAccent = Color(0xFFFFFFFF),
            accentSoft = Color(0xFFE0EEF7), accentBorder = Color(0xFFA9C8DE),
            divider = Color(0xFFE1EAF1), border = Color(0xFFD8E4ED),
            active = Color(0xFF2E8F5C), activeStrong = Color(0xFF278052),
            activeSoft = Color(0xFFE2F3E9), activeBorder = Color(0xFFBCDCC9),
            error = Color(0xFFC95C61), errorSoft = Color(0xFFF9ECEE),
        ),
        dark = false,
    ),
    GRAPHITE(
        "graphite", "Graphite and amber",
        DetourColors(
            background = Color(0xFF121212),
            surface = Color(0xFF1B1B1D), surfaceSoft = Color(0xFF1F1F22), surfaceSelected = Color(0xFF2A2A2F),
            textPrimary = Color(0xFFECEAF2), textSecondary = Color(0xFFA5A1B0), textMuted = Color(0xFF6E6A7A),
            accent = Color(0xFFE0A32E), onAccent = Color(0xFF17130A),
            accentSoft = Color(0xFF2E2614), accentBorder = Color(0xFF6B5518),
            divider = Color(0xFF26262A), border = Color(0xFF2E2E33),
            active = Color(0xFF3FA46B), activeStrong = Color(0xFF54BA7F),
            activeSoft = Color(0xFF14261B), activeBorder = Color(0xFF2A5238),
            error = Color(0xFFF87171), errorSoft = Color(0xFF331B1B),
        ),
        dark = true,
    ),
    LAVENDA(
        "lavenda", "Lavender",
        DetourColors(
            background = Color(0xFFF7F5FB),
            surface = Color(0xFFFCFBFD), surfaceSoft = Color(0xFFF8F6FB), surfaceSelected = Color(0xFFEEEAF8),
            textPrimary = Color(0xFF29273A), textSecondary = Color(0xFF777382), textMuted = Color(0xFF9994A3),
            accent = Color(0xFF7162B8), onAccent = Color(0xFFFFFFFF),
            accentSoft = Color(0xFFE9E4F5), accentBorder = Color(0xFFBDB5DB),
            divider = Color(0xFFE8E4EC), border = Color(0xFFE3DEE9),
            active = Color(0xFF319467), activeStrong = Color(0xFF28875B),
            activeSoft = Color(0xFFE7F3EC), activeBorder = Color(0xFFBFDDCC),
            error = Color(0xFFC95C61), errorSoft = Color(0xFFF9ECEE),
            // Лавандово-серый тон, растворённый в фоне: дальний слой почти невидим.
        ),
        dark = false,
    );

    val scheme: ColorScheme by lazy {
        val c = colors
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
        if (dark) darkColorScheme(
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

    /** Стиль статусной карточки и главной кнопки под состояние туннеля. */
    fun statusFor(state: dev.triplet.app.vpn.VpnState): StatusStyle = when (state) {
        dev.triplet.app.vpn.VpnState.Active -> StatusStyle(colors.activeSoft, colors.activeStrong, colors.activeBorder)
        dev.triplet.app.vpn.VpnState.Starting -> StatusStyle(colors.accentSoft, colors.accent, colors.accentBorder)
        is dev.triplet.app.vpn.VpnState.Failed -> StatusStyle(colors.errorSoft, colors.error, colors.error.copy(alpha = .35f))
        dev.triplet.app.vpn.VpnState.Idle -> StatusStyle(colors.surface, colors.textPrimary, colors.border)
    }

    companion object {
        fun byId(id: String): AppTheme = entries.firstOrNull { it.id == id } ?: LAVENDA
    }
}

fun themeLabel(theme: AppTheme): Int = when (theme) {
    AppTheme.MIDNIGHT -> R.string.theme_midnight
    AppTheme.OCEAN -> R.string.theme_ocean
    AppTheme.GRAPHITE -> R.string.theme_graphite
    AppTheme.LAVENDA -> R.string.theme_lavender
}

/** Активная тема приложения (провайдится в MainActivity). */
val LocalDetourTheme = androidx.compose.runtime.staticCompositionLocalOf<AppTheme> { AppTheme.LAVENDA }

/** Единая шкала отступов — никаких случайных 13dp/17dp/27dp. */
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

/** Скругления: сдержанные, без «огромных таблеток». */
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

/** Inter (variable) — уже в проекте, полная кириллица. */
val AppFontFamily = FontFamily(
    inter(FontWeight.Normal, 400),
    inter(FontWeight.Medium, 500),
    inter(FontWeight.SemiBold, 600),
)

/** Роли текста. Экраны не задают sp вручную — только эти стили. */
val AppTypography: Typography = Typography(
    // «Detour» на главном экране
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp, fontFamily = AppFontFamily),
    // Заголовки внутренних экранов
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp, fontFamily = AppFontFamily),
    // Заголовок статусной карточки
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, letterSpacing = 0.2.sp, fontFamily = AppFontFamily),
    // Заголовок строки настройки
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, fontFamily = AppFontFamily),
    // Основной текст и значения
    bodyLarge = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, fontFamily = AppFontFamily),
    // Вторичный текст, подписи
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp, fontFamily = AppFontFamily),
    // Примечания
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, fontFamily = AppFontFamily),
    // Кнопки
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = AppFontFamily),
    // Технические значения, сегменты
    labelMedium = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, fontFamily = AppFontFamily),
    // Имена пакетов
    labelSmall = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Normal, lineHeight = 15.sp, fontFamily = AppFontFamily),
)
