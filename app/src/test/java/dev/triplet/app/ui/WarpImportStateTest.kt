package dev.triplet.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarpImportStateTest {
    @Test fun `WARP import gate only blocks an active import`() {
        assertTrue(canStartWarpImport(WarpImportStatus.IDLE))
        assertFalse(canStartWarpImport(WarpImportStatus.IMPORTING))
        assertTrue(canStartWarpImport(WarpImportStatus.NO_COMPATIBLE_PROXIES))
        assertTrue(canStartWarpImport(WarpImportStatus.ERROR))
    }
}
