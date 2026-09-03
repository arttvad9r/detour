package dev.triplet.app.ui

import androidx.compose.ui.graphics.Color

enum class LatencyTone { GOOD, NEUTRAL, BAD, UNAVAILABLE }

internal fun latencyToneForMs(delayMs: Int?): LatencyTone = when {
    delayMs == null -> LatencyTone.UNAVAILABLE
    delayMs < 100 -> LatencyTone.GOOD
    delayMs <= 200 -> LatencyTone.NEUTRAL
    else -> LatencyTone.BAD
}

internal fun latencyColorFor(theme: AppTheme, delayMs: Int?): Color = when (latencyToneForMs(delayMs)) {
    LatencyTone.GOOD -> latencyGoodColor(theme)
    LatencyTone.NEUTRAL -> theme.colors.textSecondary
    LatencyTone.BAD -> latencyBadColor(theme)
    LatencyTone.UNAVAILABLE -> theme.colors.textMuted
}

internal fun latencyBadColor(theme: AppTheme): Color = when (theme) {
    AppTheme.DETOUR_LIGHT -> theme.colors.error
    AppTheme.DETOUR_DARK -> theme.colors.error
    AppTheme.CATPPUCCIN_LATTE -> Color(0xFFC90F39)
    AppTheme.CATPPUCCIN_MOCHA -> theme.colors.error
    AppTheme.GRUVBOX_DARK -> Color(0xFFFF806F)
    AppTheme.DRACULA -> Color(0xFFFF8585)
}

private fun latencyGoodColor(theme: AppTheme): Color = when (theme) {
    AppTheme.DETOUR_LIGHT -> theme.colors.activeStrong
    AppTheme.DETOUR_DARK -> theme.colors.activeStrong
    AppTheme.CATPPUCCIN_LATTE -> Color(0xFF28751E)
    AppTheme.CATPPUCCIN_MOCHA -> theme.colors.activeStrong
    AppTheme.GRUVBOX_DARK -> theme.colors.activeStrong
    AppTheme.DRACULA -> theme.colors.activeStrong
}
