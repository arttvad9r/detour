package dev.triplet.app.core

/** User-facing repeatability controls for automatic DPI probing. */
object DpiAutoTestOptions {
    const val MIN_ATTEMPTS = 1
    const val MAX_ATTEMPTS = 20

    // Detour keeps its existing two-observation default. ByeByeDPI exposes the
    // same 1..20 range but defaults to one request; users can choose that here.
    const val DEFAULT_ATTEMPTS = 2

    fun isValidAttempts(value: Int): Boolean = value in MIN_ATTEMPTS..MAX_ATTEMPTS
}
