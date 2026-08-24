package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessKeyParserTest {

    private val realityUri =
        "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
        "?type=tcp&security=reality&fp=chrome&sni=translate.yandex.com" +
        "&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&sid=6ba85179" +
        "&flow=xtls-rprx-vision#MyServer"

    @Test fun `parses valid reality vision tcp uri`() {
        val r = VlessKeyParser.parse(realityUri)
        assertTrue(r is ParseResult.Ok)
        val p = (r as ParseResult.Ok).profile
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", p.uuid)
        assertEquals("example.com", p.server)
        assertEquals(443, p.port)
        assertEquals("translate.yandex.com", p.sni)
        assertEquals("SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc", p.publicKey)
        assertEquals("6ba85179", p.shortId)
        assertEquals("chrome", p.fingerprint)
        assertEquals("xtls-rprx-vision", p.flow)
        assertEquals("MyServer", p.name)
    }

    @Test fun `rejects wrong scheme`() {
        assertTrue(VlessKeyParser.parse("https://example.com") is ParseResult.Err)
    }

    @Test fun `rejects unsupported transport`() {
        val ws = realityUri.replace("type=tcp", "type=ws")
        assertTrue(VlessKeyParser.parse(ws) is ParseResult.Err)
    }

    @Test fun `rejects tls without reality`() {
        val tls = realityUri.replace("security=reality&", "security=tls&").replace("&pbk=[^&]*".toRegex(), "")
        assertTrue(VlessKeyParser.parse(tls) is ParseResult.Err)
    }

    @Test fun `rejects missing public key`() {
        val noPbk = realityUri.replace("&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc", "")
        assertTrue(VlessKeyParser.parse(noPbk) is ParseResult.Err)
    }

    @Test fun `rejects garbage`() {
        assertTrue(VlessKeyParser.parse("not a uri at all") is ParseResult.Err)
    }
}
