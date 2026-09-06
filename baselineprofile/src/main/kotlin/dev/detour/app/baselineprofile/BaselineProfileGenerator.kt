package dev.detour.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        waitForHomeProfileRow()
    }

    @Test
    fun openProfiles() = rule.collect(
        packageName = TARGET_PACKAGE,
    ) {
        pressHome()
        startActivityAndWait()
        waitForHomeProfileRow()
        openProfilesFromHome()
    }
}
