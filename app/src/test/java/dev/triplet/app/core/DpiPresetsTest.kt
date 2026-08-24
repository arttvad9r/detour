package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiPresetsTest {
    @Test fun `both presets listen-ready args are not included here`() {
        // ip/port/-U добавляет DpiBackend, пресеты содержат только стратегию
        DpiPreset.entries.forEach { preset ->
            assertTrue(preset.args.none { it == "-i" || it == "-p" || it == "-U" })
        }
    }
    @Test fun `recommended uses fake packets`() {
        assertTrue(DpiPreset.RECOMMENDED.args.contains("--fake"))
    }
    @Test fun `compatible avoids fake packets`() {
        val a = DpiPreset.COMPATIBLE.args
        assertTrue(a.none { it == "--fake" || it == "-f" })
        // ladder strategy: alternating disorder/split, golden-pinned
        assertEquals(
            listOf("-d", "1", "-s", "1+s", "-d", "3+s", "-s", "6+s",
                   "-d", "9+s", "-s", "12+s", "-d", "15+s", "-s", "20+s",
                   "-d", "25+s", "-s", "30+s", "-d", "35+s", "-a", "1"),
            a,
        )
    }
    @Test fun `stable ids for persistence`() {
        assertEquals("recommended", DpiPreset.RECOMMENDED.id)
        assertEquals("compatible", DpiPreset.COMPATIBLE.id)
    }
}
