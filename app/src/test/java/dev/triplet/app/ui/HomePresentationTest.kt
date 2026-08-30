package dev.triplet.app.ui

import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.vpn.EffectiveRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePresentationTest {
    @Test fun `protocol describes effective transports`() {
        assertEquals(
            HomeProtocol.VLESS_DPI,
            homeProtocol(EffectiveRoutes(vpnPackages = setOf("vpn"), dpiPackages = setOf("dpi"))),
        )
        assertEquals(
            HomeProtocol.DPI,
            homeProtocol(EffectiveRoutes(vpnPackages = emptySet(), dpiPackages = setOf("dpi"))),
        )
        assertEquals(
            HomeProtocol.VLESS,
            homeProtocol(EffectiveRoutes(vpnPackages = setOf("vpn"), dpiPackages = emptySet())),
        )
        assertEquals(HomeProtocol.NONE, homeProtocol(EffectiveRoutes(emptySet(), emptySet())))
    }

    @Test fun `rejected shared uid routes do not advertise a transport`() {
        val rejected = EffectiveRoutes(emptySet(), emptySet(), sharedUidConflict = setOf(10001))
        assertEquals(HomeProtocol.NONE, homeProtocol(rejected))
    }

    @Test fun `server presentation follows selected profile kind`() {
        val vless =
            "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
                "?type=tcp&security=reality&fp=chrome&sni=translate.yandex.com" +
                "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
                "&flow=xtls-rprx-vision#MyServer"

        assertEquals("example.com", homeServerHost(VpnProfileKind.VLESS, vless, "Warp"))
        assertEquals("Warp", homeServerHost(VpnProfileKind.WARP, vless, "Warp"))
    }

    @Test fun `session elapsed formatting is stable and clamps negatives`() {
        assertEquals("00:00:00", formatSessionElapsed(-1))
        assertEquals("00:00:59", formatSessionElapsed(59))
        assertEquals("01:01:01", formatSessionElapsed(3661))
    }
}
