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

    @Test fun `uses the same XRay defaults as Amnezia export`() {
        val result = VlessKeyParser.parse(xrayInvite(includeDefaults = false))

        assertTrue(result is ParseResult.Ok)
        val profile = (result as ParseResult.Ok).profile
        assertEquals("chrome", profile.fingerprint)
        assertEquals("xtls-rprx-vision", profile.flow)
    }

    @Test fun `rejects Amnezia invite without a supported container`() {
        val root = JSONObject()
            .put("containers", JSONArray().put(JSONObject().put("container", "amnezia-openvpn")))
            .put("description", "OpenVPN only")

        assertTrue(VlessKeyParser.parse(encodeInvite(root)) is ParseResult.Err)
        assertTrue(AmneziaVpnImporter.parse(encodeInvite(root)) is AmneziaVpnImportResult.Unsupported)
    }

    @Test fun `rejects unsupported XRay transport`() {
        val result = AmneziaVpnImporter.parse(xrayInvite(network = "xhttp"))
        assertTrue(result is AmneziaVpnImportResult.Unsupported)
    }

    @Test fun `rejects malformed container entries instead of ignoring them`() {
        val valid = decodeRoot(xrayInvite())
        valid.getJSONArray("containers").put("not-an-object")
        assertTrue(AmneziaVpnImporter.parse(encodeInvite(valid)) is AmneziaVpnImportResult.Invalid)
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

    private fun xrayInvite(network: String = "tcp", includeDefaults: Boolean = true): String {
        val user = JSONObject()
            .put("encryption", "none")
            .put("id", "b831381d-6324-4d53-ad4f-8cda48b30811")
        if (includeDefaults) user.put("flow", "xtls-rprx-vision")

        val vnext = JSONObject()
            .put("address", "203.0.113.10")
            .put("port", 443)
            .put("users", JSONArray().put(user))
        val reality = JSONObject()
            .put("publicKey", "SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc")
            .put("serverName", "www.example.com")
            .put("shortId", "6ba85179")
        if (includeDefaults) reality.put("fingerprint", "firefox")

        val stream = JSONObject().put("realitySettings", reality)
        if (includeDefaults || network != "tcp") {
            stream.put("network", network)
        }
        if (includeDefaults) stream.put("security", "reality")

        val outbound = JSONObject()
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream)
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

    private fun decodeRoot(invite: String): JSONObject {
        val compressed = Base64.getUrlDecoder().decode(invite.removePrefix("vpn://"))
        val declaredSize = ((compressed[0].toInt() and 0xff) shl 24) or
            ((compressed[1].toInt() and 0xff) shl 16) or
            ((compressed[2].toInt() and 0xff) shl 8) or
            (compressed[3].toInt() and 0xff)
        val inflater = java.util.zip.Inflater()
        inflater.setInput(compressed, 4, compressed.size - 4)
        val output = ByteArray(declaredSize)
        val length = inflater.inflate(output)
        inflater.end()
        check(length == declaredSize)
        return JSONObject(String(output, StandardCharsets.UTF_8))
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