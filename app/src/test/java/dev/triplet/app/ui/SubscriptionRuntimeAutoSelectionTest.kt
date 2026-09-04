package dev.triplet.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRuntimeAutoSelectionTest {
    @Test fun `automatic selection only runs from idle state`() {
        assertTrue(shouldAutoSelectSubscriptionNode(SubscriptionSelectionStatus.IDLE))
        assertFalse(shouldAutoSelectSubscriptionNode(SubscriptionSelectionStatus.SAVING))
        assertFalse(shouldAutoSelectSubscriptionNode(SubscriptionSelectionStatus.ERROR))
    }

    @Test fun `catalog refresh keeps only selections that still exist`() {
        val available = setOf("Node A", "Node B")

        assertEquals("Node B", retainedSubscriptionSelection("Node B", available))
        assertNull(retainedSubscriptionSelection("Removed node", available))
        assertNull(retainedSubscriptionSelection(null, available))
    }
}
