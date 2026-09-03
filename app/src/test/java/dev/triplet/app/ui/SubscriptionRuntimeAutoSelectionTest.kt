package dev.triplet.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRuntimeAutoSelectionTest {
    @Test fun `automatic selection only runs from idle state`() {
        assertTrue(shouldAutoSelectSubscriptionNode(SubscriptionSelectionStatus.IDLE))
        assertFalse(shouldAutoSelectSubscriptionNode(SubscriptionSelectionStatus.SAVING))
        assertFalse(shouldAutoSelectSubscriptionNode(SubscriptionSelectionStatus.ERROR))
    }
}
