package dev.triplet.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiViewModelStateTest {
    @Test fun `saving state disables duplicate custom save`() {
        val state = dpiUiState(
            settings = null,
            customDraft = "-d 1 -s 2",
            editingOverride = true,
            saveState = DpiSaveState.SAVING,
        )

        assertEquals(DpiSaveState.SAVING, state.saveState)
        assertFalse(state.canSaveCustom)
    }

    @Test fun `failed custom save remains retryable`() {
        val state = dpiUiState(
            settings = null,
            customDraft = "-d 1 -s 2",
            editingOverride = true,
            saveState = DpiSaveState.ERROR,
        )

        assertEquals(DpiSaveState.ERROR, state.saveState)
        assertTrue(state.canSaveCustom)
    }
}
