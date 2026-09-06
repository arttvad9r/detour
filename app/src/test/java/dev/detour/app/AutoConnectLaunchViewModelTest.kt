package dev.detour.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectLaunchViewModelTest {
    @Test fun `same activity owner claims launch only once`() {
        val owner = AutoConnectLaunchViewModel()

        assertTrue(owner.claimLaunch())
        assertFalse(owner.claimLaunch())
    }

    @Test fun `new activity owner can auto-connect again`() {
        val previous = AutoConnectLaunchViewModel()
        assertTrue(previous.claimLaunch())
        assertFalse(previous.claimLaunch())

        assertTrue(AutoConnectLaunchViewModel().claimLaunch())
    }
}
