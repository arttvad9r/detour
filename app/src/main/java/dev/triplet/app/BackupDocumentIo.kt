package dev.triplet.app

import android.content.Context
import android.net.Uri
import dev.triplet.app.core.SettingsBackup
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal fun readBackupDocument(context: Context, uri: String): String? {
    val input = requireNotNull(context.contentResolver.openInputStream(Uri.parse(uri)))
    return input.use { readBackupDocument(it) }
}

internal fun readBackupDocument(input: InputStream): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > SettingsBackup.MAX_BYTES) return null
        out.write(buffer, 0, count)
    }
    return out.toString(Charsets.UTF_8.name())
}

internal fun writeBackupDocument(context: Context, uri: String, json: String) {
    val output = requireNotNull(context.contentResolver.openOutputStream(Uri.parse(uri)))
    output.use { writeBackupDocument(it, json) }
}

internal fun writeBackupDocument(output: OutputStream, json: String) {
    output.write(json.toByteArray(Charsets.UTF_8))
}
