package dev.detour.app

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

    @Test fun `routed package name always affects active routes`() {
        assertTrue(
            packageChangeAffectsRoutes(
                packageName = "com.routed",
                changedUid = -1,
                routedPackages = setOf("com.routed"),
                routedUids = listOf(null),
            ),
        )
    }

    @Test fun `unconfigured package sharing routed uid affects active routes`() {
        assertTrue(
            packageChangeAffectsRoutes(
                packageName = "com.sibling",
                changedUid = 12345,
                routedPackages = setOf("com.routed"),
                routedUids = listOf(12345),
            ),
        )
    }

    @Test fun `unrelated package uid does not affect active routes`() {
        assertFalse(
            packageChangeAffectsRoutes(
                packageName = "com.other",
                changedUid = 54321,
                routedPackages = setOf("com.routed"),
                routedUids = listOf(12345),
            ),
        )
        assertFalse(
            packageChangeAffectsRoutes(
                packageName = "com.other",
                changedUid = -1,
                routedPackages = setOf("com.routed"),
                routedUids = listOf(12345),
            ),
        )
    }
}
