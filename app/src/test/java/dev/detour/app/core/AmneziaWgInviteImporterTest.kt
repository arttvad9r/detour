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

class AmneziaWgInviteImporterTest {

    @Test fun `decodes AmneziaWG 3_1 vpn guest invite`() {
        val clientConfig = JSONObject()
            .put("config", AmneziaWg3ImporterTest.nativeAwg3())
            .put("protocol_version", "3.1")
        val container = JSONObject()
            .put("container", "amnezia-awg2")
            .put("awg", JSONObject().put("last_config", clientConfig.toString()))
        val root = JSONObject()
            .put("containers", JSONArray().put(container))
            .put("defaultContainer", "amnezia-awg2")
            .put("description", "My AWG server")

        val result = AmneziaVpnImporter.parse(encodeInvite(root))
        assertTrue(result is AmneziaVpnImportResult.AmneziaWg)
        val profile = (result as AmneziaVpnImportResult.AmneziaWg).profile
        val proxy = profile.proxies.single()
        assertEquals("My AWG server", profile.name)
        assertEquals(3, proxy.amnezia.version)
        assertEquals("peer-psk", proxy.preSharedKey)
        assertEquals(30, proxy.persistentKeepalive)
        assertEquals("header-key", proxy.amnezia.headerProtectionKey)
    }

    @Test fun `uses defaultContainer when invite contains XRay and AWG`() {
        val xrayContainer = JSONObject().put("container", "amnezia-xray").put("xray", JSONObject())
        val awgClient = JSONObject().put("config", AmneziaWg3ImporterTest.nativeAwg3())
        val awgContainer = JSONObject()
            .put("container", "amnezia-awg2")
            .put("awg", JSONObject().put("last_config", awgClient.toString()))
        val root = JSONObject()
            .put("containers", JSONArray().put(xrayContainer).put(awgContainer))
            .put("defaultContainer", "amnezia-awg2")

        assertTrue(AmneziaVpnImporter.parse(encodeInvite(root)) is AmneziaVpnImportResult.AmneziaWg)
    }

    private fun encodeInvite(root: JSONObject): String {
        val json = root.toString().toByteArray(StandardCharsets.UTF_8)
        val compressed = ByteArrayOutputStream().apply {
            write(byteArrayOf(
                (json.size ushr 24).toByte(),
                (json.size ushr 16).toByte(),
                (json.size ushr 8).toByte(),
                json.size.toByte(),
            ))
            DeflaterOutputStream(this).use { it.write(json) }
        }.toByteArray()
        return "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }
}
