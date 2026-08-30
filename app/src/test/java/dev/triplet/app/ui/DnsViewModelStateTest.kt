package dev.triplet.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsViewModelStateTest {
    @Test fun `saving state disables duplicate custom save`() {
        val state = dnsUiState(
            settings = null,
            customDraft = "1.1.1.1",
            editingOverride = true,
            saveState = DnsSaveState.SAVING,
        )

        assertEquals(DnsSaveState.SAVING, state.saveState)
        assertFalse(state.canSaveCustom)
    }

    @Test fun `failed custom save remains retryable`() {
        val state = dnsUiState(
            settings = null,
            customDraft = "1.1.1.1",
            editingOverride = true,
            saveState = DnsSaveState.ERROR,
        )

        assertEquals(DnsSaveState.ERROR, state.saveState)
        assertTrue(state.canSaveCustom)
    }
}
