package dev.triplet.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * One restrained motion language for the whole app.
 *
 * Spatial movement is critically damped so controls settle without overshoot.
 * Opacity and color changes stay short and never move surrounding geometry.
 * Compose applies the platform animator-duration scale to these specs, so
 * Android's reduced/disabled animation setting remains authoritative.
 */
object Motion {
    const val PRESS_TONE_MS = 70
    const val COLOR_MS = 140
    const val STATE_MS = 160
    const val CONTENT_IN_MS = 150
    const val CONTENT_OUT_MS = 100

    // Full-screen page navigation needs enough time to communicate direction at
    // both 60 Hz and high refresh rates. Forward is slightly more deliberate;
    // back is shorter so returning never feels sticky.
    const val NAV_ENTER_MS = 280
    const val NAV_EXIT_MS = 240
    const val THEME_MS = 220

    // Refresh-rate votes are deliberately bounded. Touch/release boosting is
    // handled by the platform; these windows only cover motion the app knows is
    // still active after the gesture itself has finished.
    const val NAV_REFRESH_BOOST_MS = 320L
    const val LIST_REFRESH_BOOST_MS = 220L

    /** Fast VPN starts should never flash a transient "Connecting" state. */
    const val DEFERRED_BUSY_MS = 350L

    // Large rows use tonal feedback only. Scaling cards/list rows makes borders
    // visibly breathe and is especially distracting in dense settings screens.
    const val PRESS_ROW = 1f
    const val PRESS_RADIO = 1f
    const val PRESS_BUTTON = 0.992f
    const val PRESS_FAB = 0.98f
    const val PRESS_ICON = 0.97f

    val ENTER_EASING: Easing = CubicBezierEasing(0.20f, 0f, 0f, 1f)
    val EXIT_EASING: Easing = CubicBezierEasing(0.40f, 0f, 1f, 1f)
    val STANDARD_EASING: Easing = CubicBezierEasing(0.20f, 0f, 0f, 1f)

    // Critical damping removes the rubber-band feel from switches, segments and
    // list placement while retaining visible positional continuity.
    const val SPRING_DAMPING = 1f
    const val SPRING_STIFFNESS = 750f
    const val SPRING_STIFFNESS_SOFT = 550f
}
