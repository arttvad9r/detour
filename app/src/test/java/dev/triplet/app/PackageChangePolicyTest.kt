package dev.triplet.app

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageChangePolicyTest {
    @Test fun `package add and real removal restart routed vpn`() {
        assertTrue(shouldRestartVpnForPackageChange(Intent.ACTION_PACKAGE_ADDED, replacing = false))
        assertTrue(shouldRestartVpnForPackageChange(Intent.ACTION_PACKAGE_ADDED, replacing = true))
        assertTrue(shouldRestartVpnForPackageChange(Intent.ACTION_PACKAGE_REMOVED, replacing = false))
    }

    @Test fun `replacement removal and package changed only invalidate inventory`() {
        assertFalse(shouldRestartVpnForPackageChange(Intent.ACTION_PACKAGE_REMOVED, replacing = true))
        assertFalse(shouldRestartVpnForPackageChange(Intent.ACTION_PACKAGE_CHANGED, replacing = false))
    }
}
