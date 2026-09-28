package dev.detour.app.core

import android.content.Context
import android.text.format.Formatter
import dev.detour.app.R
import org.json.JSONObject

/** Mihomo tunnel counters; rates cover the engine's last one-second window. */
data class TunnelTrafficStats(
    val uploadBytesPerSecond: Long = 0,
    val downloadBytesPerSecond: Long = 0,
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
) {
    val totalBytes: Long get() = uploadedBytes + downloadedBytes
}

/**
 * Null means the engine has no counters to report (not running, or a DPI-only
 * session without Mihomo), which the UI shows as "no traffic line" rather than
 * a misleading row of zeros.
 */
fun parseTunnelTrafficStats(raw: String): TunnelTrafficStats? {
    if (raw.isBlank() || raw.length > 8 * 1024) return null
    return runCatching {
        val json = JSONObject(raw)
        fun nonNegative(name: String): Long = json.optLong(name, 0L).coerceAtLeast(0L)
        TunnelTrafficStats(
            uploadBytesPerSecond = nonNegative("uploadBytesPerSecond"),
            downloadBytesPerSecond = nonNegative("downloadBytesPerSecond"),
            uploadedBytes = nonNegative("uploadedBytes"),
            downloadedBytes = nonNegative("downloadedBytes"),
        )
    }.getOrNull()
}

/** Localized "↓ 1.2 MB/s  ↑ 45 kB/s"; units follow the device locale. */
fun formatTunnelTrafficRates(context: Context, stats: TunnelTrafficStats): String =
    context.getString(
        R.string.traffic_rates,
        formatTrafficRate(context, stats.downloadBytesPerSecond),
        formatTrafficRate(context, stats.uploadBytesPerSecond),
    )

private fun formatTrafficRate(context: Context, bytesPerSecond: Long): String =
    context.getString(R.string.traffic_rate, Formatter.formatShortFileSize(context, bytesPerSecond))
