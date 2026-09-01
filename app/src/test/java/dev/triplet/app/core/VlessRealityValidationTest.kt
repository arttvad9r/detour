package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessRealityValidationTest {
    private val uri =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
            "?type=tcp&security=reality&fp=chrome&sni=translate.yandex.com" +
            "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
            "&flow=xtls-rprx-vision"

    @Test fun `empty short id matches pinned mihomo reality parser`() {
        val result = VlessKeyParser.parse(uri.replace("&sid=6ba85179", ""))
        assertTrue(result is ParseResult.Ok)
        assertEquals("", (result as ParseResult.Ok).profile.shortId)
    }

    @Test fun `public key must decode to exactly thirty two bytes`() {
        val oldRegexAccepted = "A".repeat(32)
        val result = VlessKeyParser.parse(
            uri.replace(
                "SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc",
                oldRegexAccepted,
            ),
        )
        assertTrue(result is ParseResult.Err)
    }

    @Test fun `short id must contain complete hex bytes`() {
        assertTrue(VlessKeyParser.parse(uri.replace("sid=6ba85179", "sid=abc")) is ParseResult.Err)
        assertTrue(VlessKeyParser.parse(uri.replace("sid=6ba85179", "sid=0011223344556677")) is ParseResult.Ok)
    }

    @Test fun `shortened java eight UUID form is rejected on every Android version`() {
        val shortened = uri.replace(
            "b831381d-6324-4d53-ad4f-8cda48b30811",
            "1-1-1-1-1",
        )
        assertTrue(VlessKeyParser.parse(shortened) is ParseResult.Err)
    }

    @Test fun `uppercase canonical UUID is accepted and normalized`() {
        val upper = "B831381D-6324-4D53-AD4F-8CDA48B30811"
        val result = VlessKeyParser.parse(
            uri.replace("b831381d-6324-4d53-ad4f-8cda48b30811", upper),
        )
        assertTrue(result is ParseResult.Ok)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", (result as ParseResult.Ok).profile.uuid)
    }
}
