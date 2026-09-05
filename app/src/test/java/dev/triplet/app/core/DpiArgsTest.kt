package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        assertFalse(DpiArgs.isValid("-i 0.0.0.0 -p 9999"))
        assertFalse(DpiArgs.isValid("-U"))
    }

    @Test fun `auto domain plan resolves to compiled trusted host groups`() {
        val plan = DpiAutoDomainPlan.of(
            mapOf(
                "youtube.com" to "split-sni",
                "discord.com" to "disorder-1",
            ),
        )

        val args = DpiArgs.resolve(
            preset = DpiPreset.AUTO,
            customRaw = "",
            autoDomainPlan = plan,
        )

        assertTrue(args.contains(":youtube.com"))
        assertTrue(args.contains(":discord.com"))
        assertTrue(args.contains("-A"))
    }

    @Test fun `auto resolution rejects conflicting global and domain plans`() {
        val plan = DpiAutoDomainPlan.of(mapOf("youtube.com" to "split-sni"))
        assertThrows(IllegalArgumentException::class.java) {
            DpiArgs.resolve(
                preset = DpiPreset.AUTO,
                customRaw = "",
                autoCandidateId = "split-sni",
                autoDomainPlan = plan,
            )
        }
    }

    @Test fun `udp fake count matches pinned ciadpi integer parser`() {
        assertTrue(DpiArgs.isValid("-a 0"))
        assertTrue(DpiArgs.isValid("-a 0x10"))
        assertTrue(DpiArgs.isValid("-a 010"))
        assertFalse(DpiArgs.isValid("-a nope"))
        assertFalse(DpiArgs.isValid("-a -1"))
        assertFalse(DpiArgs.isValid("-a 2147483648"))
        assertFalse(DpiArgs.isValid("-a 08"))
    }

    @Test fun `timeout requires positive finite decimal seconds`() {
        assertTrue(DpiArgs.isValid("--timeout 3"))
        assertTrue(DpiArgs.isValid("--timeout .5"))
        assertTrue(DpiArgs.isValid("--timeout 1e-2"))
        assertFalse(DpiArgs.isValid("--timeout nope"))
        assertFalse(DpiArgs.isValid("--timeout 0"))
        assertFalse(DpiArgs.isValid("--timeout -1"))
        assertFalse(DpiArgs.isValid("--timeout Infinity"))
        assertFalse(DpiArgs.isValid("--timeout 1f"))
    }

    @Test fun `timeout matches pinned uint millisecond boundary after float rounding`() {
        assertTrue(DpiArgs.isValid("--timeout 4294967"))
        assertFalse(DpiArgs.isValid("--timeout 4294967.5"))
    }

    @Test fun `split and disorder retain pinned parser compatibility`() {
        assertTrue(DpiArgs.isValid("-s 1+s -d 3+s"))
        // The bundled v0.17.3 parse_offset is permissive and maps this to offset 0.
        // Do not silently tighten custom strategies beyond the shipped binary.
        assertTrue(DpiArgs.isValid("-s nope"))
    }
}
