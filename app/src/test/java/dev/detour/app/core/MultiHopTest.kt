package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiHopTest {
    @Test fun `missing entry stays disabled`() {
        assertNull(MultiHopEntryRef.fromStored(null))
        assertNull(MultiHopEntryRef.fromStored(""))
        assertNull(MultiHopEntryRef.fromStored("   "))
    }

    @Test fun `supported entries round trip`() {
        val vless = MultiHopEntryRef.Vless("profile-123")
        assertEquals(vless, MultiHopEntryRef.fromStored(MultiHopEntryRef.toStored(vless)))
        assertSame(MultiHopEntryRef.Warp, MultiHopEntryRef.fromStored("warp"))
        assertEquals("warp", MultiHopEntryRef.toStored(MultiHopEntryRef.Warp))
    }

    @Test fun `corrupt non empty entry fails closed`() {
        assertSame(MultiHopEntryRef.Invalid, MultiHopEntryRef.fromStored("future:node"))
        assertSame(MultiHopEntryRef.Invalid, MultiHopEntryRef.fromStored("vless:"))
        assertSame(MultiHopEntryRef.Invalid, MultiHopEntryRef.fromStored("vless:bad:id"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid entry cannot be persisted`() {
        MultiHopEntryRef.toStored(MultiHopEntryRef.Invalid)
    }

    @Test fun `entry detects only direct self cycles`() {
        val entry = MultiHopEntryRef.Vless("entry")
        assertTrue(entry.conflictsWithExit(VpnProfileKind.VLESS, "entry"))
        assertFalse(entry.conflictsWithExit(VpnProfileKind.VLESS, "exit"))
        assertFalse(entry.conflictsWithExit(VpnProfileKind.SUBSCRIPTION, "entry"))
        assertTrue(MultiHopEntryRef.Warp.conflictsWithExit(VpnProfileKind.WARP, null))
        assertFalse(MultiHopEntryRef.Warp.conflictsWithExit(VpnProfileKind.VLESS, "exit"))
        assertTrue(MultiHopEntryRef.Invalid.conflictsWithExit(VpnProfileKind.SUBSCRIPTION, null))
    }
}
