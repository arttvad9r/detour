package dev.detour.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiPresetsTest {
    @Test fun `both presets listen-ready args are not included here`() {
        // ip/port/-U добавляет DpiBackend, пресеты содержат только стратегию
        DpiPreset.entries.forEach { preset ->
            assertTrue(preset.args.none { it == "-i" || it == "-p" || it == "-U" })
        }
    }
    @Test fun `recommended avoids fake packets`() {
        // fake-пакеты на сети МТС не нужны и мешают: лестница без -f
        assertFalse(DpiPreset.RECOMMENDED.args.contains("--fake"))
    }
    @Test fun `recommended is the tuned ladder with setup timeout`() {
        val a = DpiPreset.RECOMMENDED.args
        // ladder strategy (МТС Вологда) + fast-fail на мёртвых GGC-нодах; golden-pinned
        assertEquals(
            listOf("-d", "1", "-s", "1+s", "-d", "3+s", "-s", "6+s",
                   "-d", "9+s", "-s", "12+s", "-d", "15+s", "-s", "20+s",
                   "-d", "25+s", "-s", "30+s", "-d", "35+s", "-a", "1",
                   "--timeout", "3"),
            a,
        )
    }
    @Test fun `stable ids for persistence`() {
        assertEquals("recommended", DpiPreset.RECOMMENDED.id)
        assertEquals("custom", DpiPreset.CUSTOM.id)
        // легаси-id «compatible» мигрирует на RECOMMENDED
        assertEquals(DpiPreset.RECOMMENDED, DpiPreset.byId("compatible"))
    }
}
