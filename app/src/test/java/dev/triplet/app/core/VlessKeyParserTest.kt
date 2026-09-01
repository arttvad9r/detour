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
        assertTrue(!p.isSubscription)
    }

    @Test fun `parses https subscription without exposing token as name`() {
        val r = VlessKeyParser.parse("https://subscription.example/opaque-token")
        assertTrue(r is ParseResult.Ok)
        val p = (r as ParseResult.Ok).profile
        assertTrue(p.isSubscription)
        assertEquals("https://subscription.example/opaque-token", p.subscriptionUrl)
        assertEquals("subscription.example", p.server)
        assertEquals(443, p.port)
        assertEquals("subscription.example", p.name)
    }

    @Test fun `rejects insecure subscription and unrelated schemes`() {
        assertTrue(VlessKeyParser.parse("http://subscription.example/secret") is ParseResult.Err)
        assertTrue(VlessKeyParser.parse("trojan://example.com") is ParseResult.Err)
    }

    @Test fun `rejects subscription fragments and invalid hosts`() {
        assertTrue(VlessKeyParser.parse("https://subscription.example/key#ignored") is ParseResult.Err)
        assertTrue(VlessKeyParser.parse("https:///missing-host") is ParseResult.Err)
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

    @Test fun `rejects malformed uuid and ports`() {
        assertTrue(VlessKeyParser.parse(realityUri.replace("b831381d-6324-4d53-ad4f-8cda48b30811", "bad")) is ParseResult.Err)
        assertTrue(VlessKeyParser.parse(realityUri.replace(":443", ":0")) is ParseResult.Err)
        assertTrue(VlessKeyParser.parse(realityUri.replace(":443", ":65536")) is ParseResult.Err)
    }

    @Test fun `rejects control character and malformed reality values`() {
        assertTrue(VlessKeyParser.parse(realityUri.replace("example.com", "example.com%0A")) is ParseResult.Err)
        assertTrue(VlessKeyParser.parse(realityUri.replace("sid=6ba85179", "sid=not-hex")) is ParseResult.Err)
        assertTrue(VlessKeyParser.parse(realityUri.replace("fp=chrome", "fp=unknown")) is ParseResult.Err)
    }
}
