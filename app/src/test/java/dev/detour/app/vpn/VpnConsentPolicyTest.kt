package dev.detour.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConsentPolicyTest {

    @Test fun preAndroid13NeverRequestsNotificationPermission() {
        assertFalse(
            shouldRequestTileNotificationPermission(
                sdkInt = 32,
                granted = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test fun android13RequestsMissingPermissionOnFirstPrompt() {
        assertTrue(
            shouldRequestTileNotificationPermission(
                sdkInt = 33,
                granted = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test fun grantedPermissionDoesNotPromptAgain() {
        assertFalse(
            shouldRequestTileNotificationPermission(
                sdkInt = 37,
                granted = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test fun priorDenialIsNotRepeatedFromQuickSettings() {
        assertFalse(
            shouldRequestTileNotificationPermission(
                sdkInt = 37,
                granted = false,
                shouldShowRationale = true,
            ),
        )
    }
}
