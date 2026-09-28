package dev.detour.app.core

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Reads a profile link from a QR code in a picture the user picked, typically a
 * screenshot. Decoding is offline and needs neither camera nor Play Services.
 */
object QrImageDecoder {
    /** Screenshots are far larger than a QR needs; this bounds memory and time. */
    private const val MAX_DECODE_EDGE_PX = 1600

    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    /** Returns the decoded text, or null when the image holds no readable QR. */
    fun decode(resolver: ContentResolver, uri: Uri): String? {
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val edge = maxOf(info.size.width, info.size.height)
                if (edge > MAX_DECODE_EDGE_PX) {
                    val scale = MAX_DECODE_EDGE_PX.toFloat() / edge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }.getOrNull() ?: return null
        return try {
            decode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return decodePixels(pixels, bitmap.width, bitmap.height)
    }

    /** ARGB pixel rows in, QR text out; separated from Android for unit tests. */
    internal fun decodePixels(pixels: IntArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val source = RGBLuminanceSource(width, height, pixels)
        // Dark-mode screenshots often show light modules on a dark ground.
        for (candidate in listOf(source, source.invert())) {
            try {
                val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(candidate)), hints)
                return result.text?.trim()?.takeIf { it.isNotEmpty() }
            } catch (_: NotFoundException) {
                // Try the next candidate.
            } catch (_: com.google.zxing.ReaderException) {
                // Checksum/format errors: treat the same as no QR.
            }
        }
        return null
    }
}
