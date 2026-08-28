package dev.triplet.app.ui

/**
 * One restrained motion language for the whole app.
 *
 * Spatial movement uses springs; opacity/color changes use short tweens. Distances
 * stay small so motion communicates hierarchy without slowing a utility app down.
 */
object Motion {
    const val PRESS_TONE_MS = 70
    const val COLOR_MS = 140
    const val STATE_MS = 160
    const val CONTENT_IN_MS = 150
    const val CONTENT_OUT_MS = 90
    const val NAV_ENTER_MS = 210
    const val NAV_EXIT_MS = 145
    const val THEME_MS = 220

    /** Fast VPN starts should never flash a transient "Connecting" state. */
    const val DEFERRED_BUSY_MS = 350L

    const val PRESS_ROW = 0.994f
    const val PRESS_RADIO = 0.996f
    const val PRESS_BUTTON = 0.985f
    const val PRESS_FAB = 0.965f
    const val PRESS_ICON = 0.94f

    const val SPRING_DAMPING = 0.78f
    const val SPRING_STIFFNESS = 800f
    const val SPRING_STIFFNESS_SOFT = 650f
}
