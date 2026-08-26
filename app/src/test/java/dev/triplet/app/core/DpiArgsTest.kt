package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiArgsTest {
    @Test fun `fixed presets ignore custom string`() {
        assertEquals(
            DpiPreset.RECOMMENDED.args,
            DpiArgs.resolve(DpiPreset.RECOMMENDED, "-s 9 -d 9"),
        )
    }
    @Test fun `custom preset tokenizes on any whitespace`() {
        assertEquals(
            listOf("-s", "1+s", "-d", "3+s", "-a", "1"),
            DpiArgs.resolve(DpiPreset.CUSTOM, " -s 1+s\n-d\t3+s   -a 1 "),
        )
    }
    @Test fun `blank custom yields empty args`() {
        assertTrue(DpiArgs.resolve(DpiPreset.CUSTOM, "   ").isEmpty())
    }
    @Test fun `token count is capped`() {
        val raw = (1..200).joinToString(" ") { "x$it" }
        assertEquals(64, DpiArgs.tokenize(raw).size)
    }
    @Test fun `service arguments are rejected`() {
        assertTrue(DpiArgs.isValid("-s 1+s -d 3+s --timeout 3"))
        assertTrue(!DpiArgs.isValid("-i 0.0.0.0 -p 9999"))
        assertTrue(!DpiArgs.isValid("-U"))
    }
}
