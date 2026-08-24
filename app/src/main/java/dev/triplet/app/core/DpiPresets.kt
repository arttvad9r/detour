package dev.triplet.app.core

/**
 * Fixed ByeDPI strategy sets. Exact values are tuned during device
 * acceptance (spec: two presets only, no arbitrary arguments).
 */
enum class DpiPreset(val id: String, val args: List<String>) {
    RECOMMENDED(
        "recommended",
        listOf("--fake", "-1", "--ttl", "8",
               "--auto=torst,ssl_err", "--timeout", "3",
               "--fake", "-1", "--ttl", "5"),
    ),
    COMPATIBLE(
        "compatible",
        // Ladder strategy proven on the user's DPI-blocking carrier
        // (OnePlus acceptance, 2026-08-24; matches ByeByeDPI app preset).
        listOf("-d", "1", "-s", "1+s", "-d", "3+s", "-s", "6+s",
               "-d", "9+s", "-s", "12+s", "-d", "15+s", "-s", "20+s",
               "-d", "25+s", "-s", "30+s", "-d", "35+s", "-a", "1"),
    );

    companion object {
        fun byId(id: String): DpiPreset = entries.firstOrNull { it.id == id } ?: RECOMMENDED
    }
}
