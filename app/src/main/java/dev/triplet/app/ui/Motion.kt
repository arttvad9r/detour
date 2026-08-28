package dev.triplet.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * One restrained motion language for the whole app.
 *
 * Spatial movement uses springs; opacity/color changes use short tweens. Compose
 * applies the platform animator-duration scale to these specs, so Android's reduced
 * animation / disabled animation setting is respected without a parallel app toggle.
 */
object Motion {
    // Keep press feedback responsive, but let visible state transitions breathe.
    const val PRESS_TONE_MS = 80
    const val COLOR_MS = 170
    const val STATE_MS = 200
    const val CONTENT_IN_MS = 190
    const val CONTENT_OUT_MS = 120
    const val NAV_ENTER_MS = 260
    const val NAV_EXIT_MS = 180
    const val THEME_MS = 280

    /** Fast VPN starts should never flash a transient "Connecting" state. */
    const val DEFERRED_BUSY_MS = 350L

    const val PRESS_ROW = 0.994f
    const val PRESS_RADIO = 0.996f
    const val PRESS_BUTTON = 0.985f
    const val PRESS_FAB = 0.965f
    const val PRESS_ICON = 0.94f

    // Material-like asymmetric curves: entrances settle softly; exits get out of
    // the way a little faster. These matter more than simply increasing duration.
    val ENTER_EASING: Easing = CubicBezierEasing(0.20f, 0f, 0f, 1f)
    val EXIT_EASING: Easing = CubicBezierEasing(0.40f, 0f, 1f, 1f)
    val STANDARD_EASING: Easing = CubicBezierEasing(0.20f, 0f, 0f, 1f)

    // Softer travel springs than press springs. Segments/switches should visibly
    // travel to the next state, while pressed controls still respond immediately.
    const val SPRING_DAMPING = 0.82f
    const val SPRING_STIFFNESS = 700f
    const val SPRING_STIFFNESS_SOFT = 440f
}
