package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoDomainCatalogTest {
    private val byId = DpiAutoDomainCatalog.default.associateBy { it.id }

    @Test fun `catalog contains pinned ByeByeDPI hosts plus stable Detour anchors`() {
        // ByeByeDPI contributes 13/19/21/52 hosts. Detour retains one existing
        // anchor in YouTube, GoogleVideo and Discord respectively.
        assertEquals(14, byId.getValue("youtube").targets.size)
        assertEquals(20, byId.getValue("googlevideo").targets.size)
        assertEquals(22, byId.getValue("discord").targets.size)
        assertEquals(52, byId.getValue("telegram").targets.size)
        assertEquals(108, DpiAutoDomainCatalog.default.sumOf { it.targets.size })

        assertTrue(byId.getValue("youtube").targets.any { it.host == "www.youtube.com" })
        assertTrue(byId.getValue("googlevideo").targets.any { it.host == "redirector.googlevideo.com" })
        assertTrue(byId.getValue("discord").targets.any { it.host == "gateway.discord.gg" })
    }

    @Test fun `googlevideo probes use stable parent scope`() {
        val targets = byId.getValue("googlevideo").targets
        assertTrue(targets.all { it.host.endsWith(".googlevideo.com") })
        assertTrue(targets.all { it.scopeHost == "googlevideo.com" })
    }

    @Test fun `youtube related hosts map to durable service scopes`() {
        val targets = byId.getValue("youtube").targets.associateBy { it.host }
        assertEquals("youtube.com", targets.getValue("www.youtube.com").scopeHost)
        assertEquals("youtube.com", targets.getValue("signaler-pa.youtube.com").scopeHost)
        assertEquals("googlevideo.com", targets.getValue("manifest.googlevideo.com").scopeHost)
        assertEquals("googleapis.com", targets.getValue("youtubei.googleapis.com").scopeHost)
        assertEquals("ytimg.com", targets.getValue("i9.ytimg.com").scopeHost)
    }

    @Test fun `telegram alternate domains retain separate rule roots`() {
        val targets = byId.getValue("telegram").targets.associateBy { it.host }
        assertEquals("telegram.org", targets.getValue("webk.telegram.org").scopeHost)
        assertEquals("telegram.me", targets.getValue("zws1.web.telegram.me").scopeHost)
        assertEquals("telegram.dog", targets.getValue("telegram.dog").scopeHost)
        assertEquals("telegra.ph", targets.getValue("telegra.ph").scopeHost)
        assertEquals("telesco.pe", targets.getValue("telesco.pe").scopeHost)
    }

    @Test fun `catalog target ids and hosts are unique inside each group`() {
        DpiAutoDomainCatalog.default.forEach { group ->
            assertEquals(group.targets.size, group.targets.map { it.id }.distinct().size)
            assertEquals(group.targets.size, group.targets.map { it.host }.distinct().size)
        }
    }
}
