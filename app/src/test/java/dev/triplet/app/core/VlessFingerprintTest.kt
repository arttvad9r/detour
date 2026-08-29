package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessFingerprintTest {
    private val base =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
        "?type=tcp&security=reality&fp=%s&sni=example.com" +
        "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
        "&flow=xtls-rprx-vision"

    @Test fun `accepts fingerprints supported by pinned mihomo`() {
        listOf("chrome", "firefox", "safari", "iOS", "android", "edge", "360", "qq", "random")
            .forEach { fingerprint ->
                assertTrue("fingerprint=$fingerprint", VlessKeyParser.parse(base.format(fingerprint)) is ParseResult.Ok)
            }
    }

    @Test fun `normalizes legacy lowercase ios fingerprint`() {
        val parsed = VlessKeyParser.parse(base.format("ios")) as ParseResult.Ok
        assertEquals("iOS", parsed.profile.fingerprint)
    }
}
