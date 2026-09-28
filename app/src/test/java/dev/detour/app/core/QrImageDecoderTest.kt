package dev.detour.app.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrImageDecoderTest {
    private fun render(text: String, invert: Boolean = false): Triple<IntArray, Int, Int> {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300)
        val dark = if (invert) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val light = if (invert) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) dark else light
        }
        return Triple(pixels, matrix.width, matrix.height)
    }

    @Test
    fun `decodes a profile link`() {
        val link = "vless://00000000-0000-0000-0000-000000000000@example.com:443?security=reality#Test"
        val (pixels, width, height) = render(link)
        assertEquals(link, QrImageDecoder.decodePixels(pixels, width, height))
    }

    @Test
    fun `decodes an inverted dark-mode code`() {
        val (pixels, width, height) = render("https://sub.example.com/token", invert = true)
        assertEquals("https://sub.example.com/token", QrImageDecoder.decodePixels(pixels, width, height))
    }

    @Test
    fun `blank image has no code`() {
        assertNull(QrImageDecoder.decodePixels(IntArray(100 * 100) { 0xFFFFFFFF.toInt() }, 100, 100))
    }
}
