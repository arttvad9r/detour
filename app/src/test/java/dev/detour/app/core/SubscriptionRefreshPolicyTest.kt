package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRefreshPolicyTest {
    @Test
    fun `provider interval is suggested when valid`() {
        assertEquals(6, SubscriptionRefreshPolicy.suggestedIntervalHours(6))
        assertEquals(24, SubscriptionRefreshPolicy.suggestedIntervalHours(null))
        assertEquals(24, SubscriptionRefreshPolicy.suggestedIntervalHours(0))
    }

    @Test
    fun `null configured interval means disabled`() {
        assertNull(SubscriptionRefreshPolicy.effectiveIntervalHours(null))
        assertEquals(12, SubscriptionRefreshPolicy.effectiveIntervalHours(12))
    }

    @Test
    fun `never refreshed subscription is immediately due`() {
        assertTrue(
            SubscriptionRefreshPolicy.isDue(
                lastUpdatedAt = null,
                intervalHours = 24,
                nowMillis = 10_000L,
            ),
        )
    }

    @Test
    fun `refresh becomes due only after configured interval`() {
        val hour = 60L * 60L * 1000L
        val last = 1_000_000L
        assertFalse(SubscriptionRefreshPolicy.isDue(last, 12, last + 11 * hour))
        assertTrue(SubscriptionRefreshPolicy.isDue(last, 12, last + 12 * hour))
    }

    @Test
    fun `clock moving backwards does not force refresh`() {
        assertFalse(SubscriptionRefreshPolicy.isDue(10_000L, 1, 9_000L))
    }
}
