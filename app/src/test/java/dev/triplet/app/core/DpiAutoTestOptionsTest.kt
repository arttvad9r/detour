package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoTestOptionsTest {
    @Test fun `attempt range matches user facing proxy test control`() {
        assertEquals(1, DpiAutoTestOptions.MIN_ATTEMPTS)
        assertEquals(20, DpiAutoTestOptions.MAX_ATTEMPTS)
        assertTrue(DpiAutoTestOptions.isValidAttempts(1))
        assertTrue(DpiAutoTestOptions.isValidAttempts(20))
        assertFalse(DpiAutoTestOptions.isValidAttempts(0))
        assertFalse(DpiAutoTestOptions.isValidAttempts(21))
    }

    @Test fun `Detour preserves two attempt reliability default`() {
        assertEquals(2, DpiAutoTestOptions.DEFAULT_ATTEMPTS)
    }
}
