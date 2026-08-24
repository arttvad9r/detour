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
        listOf("--split", "1+s",
               "--auto=torst", "--timeout", "3",
               "--split", "3+s"),
    );

    companion object {
        fun byId(id: String): DpiPreset = entries.firstOrNull { it.id == id } ?: RECOMMENDED
    }
}
