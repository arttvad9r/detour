package dev.detour.app.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarpProfileTest {
    private val profile = WarpProfile(
        id = "warp-1",
        name = "Cloudflare WARP",
        proxies = listOf(
            WarpProxy(
                name = "Finland",
                server = "fi.example.net",
                port = 4500,
                ip = "172.16.0.2",
                privateKey = "private",
                publicKey = "public",
                reserved = listOf(1, 2, 3),
                allowedIps = listOf("0.0.0.0/0"),
                dns = listOf("1.1.1.1"),
                amnezia = AmneziaWgOptions(jc = 4, h1 = "1", i1 = "<b 0x01>"),
            ),
        ),
    )

    @Test fun `json roundtrip preserves WARP profile`() {
        assertEquals(profile, WarpProfile.fromJson(profile.toJson()))
    }

    @Test fun `legacy numeric H fields remain readable`() {
        val root = JSONObject(profile.toJson())
        val proxy = root.getJSONArray("proxies").getJSONObject(0)
        proxy.getJSONObject("amnezia").put("h1", 123)
        proxy.put("preSharedKey", JSONObject.NULL)
        root.put("proxies", JSONArray().put(proxy))

        val restored = WarpProfile.fromJson(root.toString())
        assertEquals("123", restored.proxies.single().amnezia.h1)
        assertNull(restored.proxies.single().preSharedKey)
    }

    @Test fun `corrupt persisted WARP is ignored`() {
        assertNull(WarpProfile.fromStored("{broken"))
    }
}
