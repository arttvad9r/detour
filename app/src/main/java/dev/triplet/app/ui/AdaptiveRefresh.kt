package dev.triplet.app.ui

import android.os.Build
import android.view.Window
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate

/**
 * Keep platform adaptive refresh enabled and let touch interactions boost the
 * window when the device supports Android's frame-rate policy APIs.
 *
 * We deliberately do not pin a concrete refresh rate such as 120 Hz: High is a
 * semantic request, so the display scheduler can choose the best supported rate
 * while Default lets LTPO/ARR panels fall back down when the UI is static.
 */
internal fun configureAdaptiveRefresh(window: Window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        window.frameRateBoostOnTouchEnabled = true
        window.isFrameRatePowerSavingsBalanced = true
    }
}

/** Request a high frame-rate category only for a bounded spatial-motion window. */
internal fun Modifier.detourHighRefresh(active: Boolean): Modifier =
    preferredFrameRate(
        if (active) FrameRateCategory.High else FrameRateCategory.Default,
    )
