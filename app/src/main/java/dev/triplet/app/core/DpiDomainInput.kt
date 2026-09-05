package dev.triplet.app.core

import java.net.IDN

/** Parsed user-provided hosts for automatic DPI testing. */
data class DpiDomainInputResult(
    val targets: List<DpiProbeTarget>,
    val invalid: List<String>,
) {
    val isValid: Boolean get() = invalid.isEmpty()
}

/**
 * Parses whitespace/comma separated domain names. URLs, ports, paths and
 * wildcard patterns are intentionally rejected: the probe requires a concrete
 * TLS hostname and sends it unchanged through SOCKS5.
 */
object DpiDomainInput {
    private const val MAX_INPUT_CHARS = 4096
    private const val MAX_DOMAINS = 64
    private val label = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun parse(raw: String): DpiDomainInputResult {
        if (raw.length > MAX_INPUT_CHARS) {
            return DpiDomainInputResult(emptyList(), listOf(raw.take(80)))
        }

        val tokens = raw
            .split(Regex("[\\s,;]+"))
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (tokens.size > MAX_DOMAINS) {
            return DpiDomainInputResult(emptyList(), tokens.drop(MAX_DOMAINS))
        }

        val invalid = mutableListOf<String>()
        val hosts = linkedSetOf<String>()
        tokens.forEach { token ->
            val host = normalize(token)
            if (host == null) invalid += token else hosts += host
        }

        return DpiDomainInputResult(
            targets = hosts.map { host -> DpiProbeTarget("custom:$host", host) },
            invalid = invalid,
        )
    }

    private fun normalize(token: String): String? {
        if (
            token.contains("://") || token.contains('/') || token.contains(':') ||
            token.contains('*') || token.contains('?') || token.startsWith('.')
        ) return null

        val trimmed = token.trim().removeSuffix(".")
        if (trimmed.isEmpty()) return null
        val ascii = runCatching {
            IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase()
        }.getOrNull() ?: return null
        if (ascii.length !in 1..253 || '.' !in ascii) return null
        val labels = ascii.split('.')
        if (labels.any { it.length !in 1..63 || !label.matches(it) }) return null
        return ascii
    }
}
