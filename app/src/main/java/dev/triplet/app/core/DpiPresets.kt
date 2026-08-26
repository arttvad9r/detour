package dev.triplet.app.core

/**
 * ByeDPI strategy presets.
 * RECOMMENDED — лестница, подобранная и проверенная на сети провайдера
 * владельца (МТС Вологда, приёмка 2026-08-24); CUSTOM — свободное поле
 * аргументов из настроек.
 */
enum class DpiPreset(val id: String, val args: List<String>) {
    RECOMMENDED(
        "recommended",
        listOf("-d", "1", "-s", "1+s", "-d", "3+s", "-s", "6+s",
               "-d", "9+s", "-s", "12+s", "-d", "15+s", "-s", "20+s",
               "-d", "25+s", "-s", "30+s", "-d", "35+s", "-a", "1",
               "--timeout", "3"),
    ),
    CUSTOM("custom", emptyList());

    companion object {
        fun byId(id: String): DpiPreset = entries.firstOrNull { it.id == id } ?: RECOMMENDED
    }
}

/** Разбор пользовательской строки аргументов ciadpi и выбор источника стратегии. */
object DpiArgs {
    private const val MAX_TOKENS = 64
    private val allowed = setOf("-d", "-s", "-a", "--timeout")

    fun resolve(preset: DpiPreset, customRaw: String): List<String> =
        if (preset == DpiPreset.CUSTOM) tokenize(customRaw) else preset.args

    fun isValid(raw: String): Boolean {
        val tokens = tokenize(raw)
        if (raw.trim().split(Regex("\\s+")).count { it.isNotBlank() } > MAX_TOKENS) return false
        if (tokens.isEmpty() || tokens.any { it.any { c -> c.code < 0x20 || c.code == 0x7f } }) return false
        if (tokens.any { it in setOf("-i", "-p", "-U", "--daemon", "--pid", "--log", "--bind", "--listen") }) return false
        var expecting = false
        tokens.forEach { token ->
            if (expecting) { expecting = false; return@forEach }
            if (token !in allowed) return false
            expecting = true
        }
        return !expecting
    }

    /** Пробелы/переводы строк -> argv; пустые токены отбрасываются, длина ограничена. */
    fun tokenize(raw: String): List<String> =
        raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(MAX_TOKENS)
}
