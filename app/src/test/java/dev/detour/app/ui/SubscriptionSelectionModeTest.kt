package dev.detour.app.ui

import dev.detour.app.core.SubscriptionSelectionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionSelectionModeTest {
    @Test
    fun `manual idle profile may choose deterministic initial node`() {
        assertTrue(
            shouldChooseInitialManualSubscriptionNode(
                SubscriptionSelectionMode.MANUAL,
                SubscriptionSelectionStatus.IDLE,
            ),
        )
    }

    @Test
    fun `automatic mode never invokes manual selector`() {
        assertFalse(
            shouldChooseInitialManualSubscriptionNode(
                SubscriptionSelectionMode.AUTO,
                SubscriptionSelectionStatus.IDLE,
            ),
        )
    }

    @Test
    fun `saving state does not start another manual selection`() {
        assertFalse(
            shouldChooseInitialManualSubscriptionNode(
                SubscriptionSelectionMode.MANUAL,
                SubscriptionSelectionStatus.SAVING,
            ),
        )
    }
}
