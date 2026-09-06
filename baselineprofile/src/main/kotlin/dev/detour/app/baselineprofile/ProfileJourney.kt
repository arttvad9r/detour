package dev.detour.app.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope

internal const val HOME_PROFILE_ROW_TAG = "home_profile_row"
internal const val PROFILES_SCREEN_TAG = "profiles_screen"
private const val UI_TIMEOUT_MS = 5_000L

internal fun MacrobenchmarkScope.waitForHomeProfileRow() {
    onElement(timeoutMs = UI_TIMEOUT_MS) {
        viewIdResourceName == HOME_PROFILE_ROW_TAG
    }
}

internal fun MacrobenchmarkScope.openProfilesFromHome() {
    onElement(timeoutMs = UI_TIMEOUT_MS) {
        viewIdResourceName == HOME_PROFILE_ROW_TAG
    }.click()
    onElement(timeoutMs = UI_TIMEOUT_MS) {
        viewIdResourceName == PROFILES_SCREEN_TAG
    }
}
