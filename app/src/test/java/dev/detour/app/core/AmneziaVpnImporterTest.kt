package dev.detour.app.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class AmneziaVpnImporterTest {

    @Test fun `parses Amnezia XRay Reality guest invite through VLESS parser`() {
        val invite = xrayInvite()

        val result = VlessKeyParser.parse(invite)

        assertTrue(result is ParseResult.Ok)
        val profile = (result as ParseResult.Ok).profile
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", profile.uuid)
        assertEquals("203.0.113.10", profile.server)
        assertEquals(443, profile.port)
        assertEquals("www.example.com", profile.sni)
        assertEquals("SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc", profile.publicKey)
        assertEquals("6ba85179", profile.shortId)
        assertEquals("firefox", profile.fingerprint)
        assertEquals("xtls-rprx-vision", profile.flow)
        assertEquals("Amnezia Invite", profile.name)
        assertTrue(!profile.isSubscription)
    }

    @Test fun `rejects Amnezia invite without an XRay container`() {
        val root = JSONObject()
            .put("containers", JSONArray().put(JSONObject().put("container", "amnezia-awg")))
            .put("description", "AWG only")

        assertTrue(VlessKeyParser.parse(encodeInvite(root)) is ParseResult.Err)
        assertTrue(AmneziaVpnImporter.parse(encodeInvite(root)) is AmneziaVpnImportResult.Unsupported)
    }

    @Test fun `rejects unsupported XRay transport`() {
        val result = AmneziaVpnImporter.parse(xrayInvite(network = "xhttp"))
        assertTrue(result is AmneziaVpnImportResult.Unsupported)
    }

    @Test fun `rejects malformed compressed invite`() {
        assertTrue(VlessKeyParser.parse("vpn://not-valid-base64%%%") is ParseResult.Err)

        val json = "{}".toByteArray(StandardCharsets.UTF_8)
        val payload = ByteArrayOutputStream().apply {
            // Deliberately lie about the qCompress uncompressed length.
            write(byteArrayOf(0, 0, 0, 99))
            DeflaterOutputStream(this).use { it.write(json) }
        }.toByteArray()
        val invite = "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
        assertTrue(VlessKeyParser.parse(invite) is ParseResult.Err)
    }

    private fun xrayInvite(network: String = "tcp"): String {
        val user = JSONObject()
            .put("encryption", "none")
            .put("flow", "xtls-rprx-vision")
            .put("id", "b831381d-6324-4d53-ad4f-8cda48b30811")
        val vnext = JSONObject()
            .put("address", "203.0.113.10")
            .put("port", 443)
            .put("users", JSONArray().put(user))
        val reality = JSONObject()
            .put("fingerprint", "firefox")
            .put("publicKey", "SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc")
            .put("serverName", "www.example.com")
            .put("shortId", "6ba85179")
        val outbound = JSONObject()
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put(
                "streamSettings",
                JSONObject()
                    .put("network", network)
                    .put("security", "reality")
                    .put("realitySettings", reality),
            )
        val lastConfig = JSONObject()
            .put("outbounds", JSONArray().put(outbound))
            .toString()
        val container = JSONObject()
            .put("container", "amnezia-xray")
            .put("xray", JSONObject().put("last_config", lastConfig))
        val root = JSONObject()
            .put("containers", JSONArray().put(container))
            .put("defaultContainer", "amnezia-xray")
            .put("description", "Amnezia Invite")
            .put("hostName", "203.0.113.10")

        return encodeInvite(root)
    }

    private fun encodeInvite(root: JSONObject): String {
        val json = root.toString().toByteArray(StandardCharsets.UTF_8)
        val compressed = ByteArrayOutputStream().apply {
            write(
                byteArrayOf(
                    (json.size ushr 24).toByte(),
                    (json.size ushr 16).toByte(),
                    (json.size ushr 8).toByte(),
                    json.size.toByte(),
                ),
            )
            DeflaterOutputStream(this).use { it.write(json) }
        }.toByteArray()
        return "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }
}
