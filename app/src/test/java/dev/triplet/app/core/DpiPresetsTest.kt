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
        assertTrue(DpiPreset.COMPATIBLE.args.none { it == "--fake" })
        assertTrue(DpiPreset.COMPATIBLE.args.contains("--split"))
    }
    @Test fun `stable ids for persistence`() {
        assertEquals("recommended", DpiPreset.RECOMMENDED.id)
        assertEquals("compatible", DpiPreset.COMPATIBLE.id)
    }
}
