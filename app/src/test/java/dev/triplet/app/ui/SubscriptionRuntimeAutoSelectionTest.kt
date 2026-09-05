package dev.triplet.app.ui

import dev.triplet.app.core.SubscriptionSelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRuntimeAutoSelectionTest {
    @Test fun `initial node selection only runs for idle manual mode`() {
        assertTrue(
            shouldChooseInitialManualSubscriptionNode(
                SubscriptionSelectionMode.MANUAL,
                SubscriptionSelectionStatus.IDLE,
            ),
        )
        assertFalse(
            shouldChooseInitialManualSubscriptionNode(
                SubscriptionSelectionMode.AUTO,
                SubscriptionSelectionStatus.IDLE,
            ),
        )
        assertFalse(
            shouldChooseInitialManualSubscriptionNode(
                SubscriptionSelectionMode.MANUAL,
                SubscriptionSelectionStatus.SAVING,
            ),
        )
        assertFalse(
            shouldChooseInitialManualSubscriptionNode(
                SubscriptionSelectionMode.MANUAL,
                SubscriptionSelectionStatus.ERROR,
            ),
        )
    }

    @Test fun `catalog refresh keeps only selections that still exist`() {
        val available = setOf("Node A", "Node B")

        assertEquals("Node B", retainedSubscriptionSelection("Node B", available))
        assertNull(retainedSubscriptionSelection("Removed node", available))
        assertNull(retainedSubscriptionSelection(null, available))
    }
}
