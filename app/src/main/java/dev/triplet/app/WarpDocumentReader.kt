package dev.triplet.app

import android.content.Context
import android.net.Uri
import dev.triplet.app.core.WarpConfigImporter
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun readWarpDocument(context: Context, uri: String): String? {
    val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return null
    return input.use { readWarpDocument(it) }
}

internal fun readWarpDocument(input: InputStream): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > WarpConfigImporter.MAX_CHARS) return null
        out.write(buffer, 0, count)
    }
    return out.toString(Charsets.UTF_8.name())
}
