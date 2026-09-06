package dev.detour.app.core

import org.json.JSONObject

data class TunnelTrafficStats(
    val uploadBytesPerSecond: Long = 0,
    val downloadBytesPerSecond: Long = 0,
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
) {
    val totalBytes: Long get() = (uploadedBytes + downloadedBytes).coerceAtLeast(0)
}

fun parseTunnelTrafficStats(raw: String): TunnelTrafficStats {
    if (raw.isBlank() || raw.length > 8 * 1024) return TunnelTrafficStats()
    return runCatching {
        val json = JSONObject(raw)
        fun nonNegative(name: String): Long = json.optLong(name, 0L).coerceAtLeast(0L)
        TunnelTrafficStats(
            uploadBytesPerSecond = nonNegative("uploadBytesPerSecond"),
            downloadBytesPerSecond = nonNegative("downloadBytesPerSecond"),
            uploadedBytes = nonNegative("uploadedBytes"),
            downloadedBytes = nonNegative("downloadedBytes"),
        )
    }.getOrDefault(TunnelTrafficStats())
}

fun formatTunnelTrafficBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1_000L -> "$safe B"
        safe < 1_000_000L -> formatScaledTraffic(safe, 1_000L, "KB")
        safe < 1_000_000_000L -> formatScaledTraffic(safe, 1_000_000L, "MB")
        safe < 1_000_000_000_000L -> formatScaledTraffic(safe, 1_000_000_000L, "GB")
        else -> formatScaledTraffic(safe, 1_000_000_000_000L, "TB")
    }
}

fun formatTunnelTrafficRate(bytesPerSecond: Long): String =
    "${formatTunnelTrafficBytes(bytesPerSecond)}/s"

private fun formatScaledTraffic(value: Long, divisor: Long, suffix: String): String {
    val whole = value / divisor
    val tenth = ((value % divisor) * 10L / divisor).toInt()
    return if (whole >= 100L || tenth == 0) "$whole $suffix" else "$whole.$tenth $suffix"
}
