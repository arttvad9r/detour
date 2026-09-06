package dev.detour.app.vpn

import dev.detour.app.core.AmneziaWgOptions
import dev.detour.app.core.DpiPreset
import dev.detour.app.core.MultiHopEntryRef
import dev.detour.app.core.VlessKey
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnOutbound
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.core.WarpProfile
import dev.detour.app.core.WarpProxy
import dev.detour.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiHopResolverTest {
    private val realityUri =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@entry.example.com:443" +
            "?type=tcp&security=reality&fp=chrome&sni=cdn.example.com" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
            "&flow=xtls-rprx-vision#Entry"

    private val warp = WarpProfile(
        id = "warp",
        name = "WARP",
        proxies = listOf(
            WarpProxy(
                name = "endpoint",
                server = "warp.example.com",
                port = 4500,
                ip = "172.16.0.2",
                privateKey = "private-key",
                publicKey = "public-key",
                reserved = listOf(1, 2, 3),
                allowedIps = listOf("0.0.0.0/0"),
                amnezia = AmneziaWgOptions(jc = 4),
            ),
        ),
    )

    private fun settings(
        keys: VlessKeys = VlessKeys(emptyList(), null),
        activeVpn: VpnProfileKind = VpnProfileKind.SUBSCRIPTION,
        entry: MultiHopEntryRef? = null,
        warpProfile: WarpProfile? = warp,
    ) = TriSettings(
        vlessKeys = keys,
        warpProfile = warpProfile,
        activeVpn = activeVpn,
        preset = DpiPreset.RECOMMENDED,
        dpiCustomArgs = "",
        autoConnect = false,
        themeId = "",
        dnsId = "",
        dnsCustom = "",
        routes = emptyMap(),
        showSystemApps = false,
        sessionStartedAt = null,
        multiHopEntry = entry,
    )

    @Test fun `disabled multi-hop resolves to null`() {
        assertNull(resolveMultiHopEntry(settings()))
    }

    @Test fun `saved VLESS entry resolves to outbound`() {
        val keys = VlessKeys(listOf(VlessKey("entry", "Entry", realityUri)), null)
        val resolved = resolveMultiHopEntry(settings(keys = keys, entry = MultiHopEntryRef.Vless("entry")))
        assertTrue(resolved is VpnOutbound.Vless)
        assertEquals("entry.example.com", (resolved as VpnOutbound.Vless).profile.server)
    }

    @Test fun `WARP entry resolves to outbound`() {
        val resolved = resolveMultiHopEntry(settings(entry = MultiHopEntryRef.Warp))
        assertEquals(VpnOutbound.Warp(warp), resolved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing VLESS entry fails closed`() {
        resolveMultiHopEntry(settings(entry = MultiHopEntryRef.Vless("missing")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subscription VLESS key cannot be entry`() {
        val keys = VlessKeys(
            listOf(VlessKey("entry", "Subscription", "https://subscription.example/token")),
            null,
        )
        resolveMultiHopEntry(settings(keys = keys, entry = MultiHopEntryRef.Vless("entry")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `same active VLESS key cannot be entry and exit`() {
        val keys = VlessKeys(listOf(VlessKey("entry", "Entry", realityUri)), "entry")
        resolveMultiHopEntry(
            settings(
                keys = keys,
                activeVpn = VpnProfileKind.VLESS,
                entry = MultiHopEntryRef.Vless("entry"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `WARP cannot be entry when WARP is exit`() {
        resolveMultiHopEntry(settings(activeVpn = VpnProfileKind.WARP, entry = MultiHopEntryRef.Warp))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `corrupt persisted entry fails closed`() {
        resolveMultiHopEntry(settings(entry = MultiHopEntryRef.Invalid))
    }
}
