package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoDomainCatalogTest {
    private val byId = DpiAutoDomainCatalog.default.associateBy { it.id }

    @Test fun `catalog matches pinned ByeByeDPI site counts`() {
        assertEquals(13, byId.getValue("youtube").targets.size)
        assertEquals(19, byId.getValue("googlevideo").targets.size)
        assertEquals(21, byId.getValue("discord").targets.size)
        assertEquals(52, byId.getValue("telegram").targets.size)
        assertEquals(105, DpiAutoDomainCatalog.default.sumOf { it.targets.size })
    }

    @Test fun `googlevideo probes use stable parent scope`() {
        val targets = byId.getValue("googlevideo").targets
        assertTrue(targets.all { it.host.endsWith(".googlevideo.com") })
        assertTrue(targets.all { it.scopeHost == "googlevideo.com" })
    }

    @Test fun `youtube related hosts map to durable service scopes`() {
        val targets = byId.getValue("youtube").targets.associateBy { it.host }
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
