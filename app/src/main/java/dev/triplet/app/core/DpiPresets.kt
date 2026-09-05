package dev.triplet.app.core

/**
 * ByeDPI strategy presets.
 * RECOMMENDED — лестница, подобранная и проверенная на сети провайдера
 * владельца (МТС Вологда, приёмка 2026-08-24); AUTO — стратегия из
 * встроенного доверенного каталога; CUSTOM — свободное поле аргументов.
 */
enum class DpiPreset(val id: String, val args: List<String>) {
    RECOMMENDED(
        "recommended",
        listOf("-d", "1", "-s", "1+s", "-d", "3+s", "-s", "6+s",
               "-d", "9+s", "-s", "12+s", "-d", "15+s", "-s", "20+s",
               "-d", "25+s", "-s", "30+s", "-d", "35+s", "-a", "1",
               "--timeout", "3"),
    ),
    AUTO("auto", emptyList()),
    CUSTOM("custom", emptyList());

    companion object {
        fun byId(id: String): DpiPreset = entries.firstOrNull { it.id == id } ?: RECOMMENDED
    }
}

/** Разбор пользовательской строки аргументов ciadpi и выбор источника стратегии. */
object DpiArgs {
    private const val MAX_TOKENS = 64
    private const val UINT_MAX = 0xffff_ffffL
    private val allowed = setOf("-d", "-s", "-a", "--timeout")
    private val decimalSeconds = Regex(
        """[+-]?(?:(?:\d+(?:\.\d*)?)|(?:\.\d+))(?:[eE][+-]?\d+)?""",
    )

    fun resolve(
        preset: DpiPreset,
        customRaw: String,
        autoCandidateId: String = "",
        autoDomainPlan: DpiAutoDomainPlan? = null,
    ): List<String> = when (preset) {
        DpiPreset.RECOMMENDED -> preset.args
        DpiPreset.CUSTOM -> tokenize(customRaw)
        DpiPreset.AUTO -> {
            if (autoDomainPlan != null) {
                require(autoCandidateId.isBlank()) { "conflicting automatic DPI strategies" }
                autoDomainPlan.compileArgs()
            } else {
                requireNotNull(DpiStrategyCatalog.byId(autoCandidateId)) {
                    "unknown automatic DPI strategy"
                }.args
            }
        }
    }

    fun isValid(raw: String): Boolean {
        val tokens = tokenize(raw)
        if (raw.trim().split(Regex("\\s+")).count { it.isNotBlank() } > MAX_TOKENS) return false
        if (tokens.isEmpty() || tokens.any { it.any { c -> c.code < 0x20 || c.code == 0x7f } }) return false
        if (tokens.any { it in setOf("-i", "-p", "-U", "--daemon", "--pid", "--log", "--bind", "--listen") }) return false

        var option: String? = null
        tokens.forEach { token ->
            val current = option
            if (current == null) {
                if (token !in allowed) return false
                option = token
            } else {
                if (!isValueValid(current, token)) return false
                option = null
            }
        }
        return option == null
    }

    private fun isValueValid(option: String, value: String): Boolean = when (option) {
        "-a" -> parseCInteger(value)?.let { it in 0..Int.MAX_VALUE.toLong() } == true
        "--timeout" -> {
            // Pinned ByeDPI v0.17.3 parses Linux timeout with strtof(), converts
            // seconds to integer milliseconds, then compares that integer to UINT_MAX.
            if (!decimalSeconds.matches(value)) false
            else value.toFloatOrNull()?.let { seconds ->
                val millis = seconds * 1000f
                seconds.isFinite() && millis.isFinite() && millis.toLong() in 1..UINT_MAX
            } == true
        }
        // -s/-d use ByeDPI's permissive parse_offset(). Do not invent a stricter
        // grammar here than the exact binary bundled by Detour accepts.
        "-s", "-d" -> true
        else -> false
    }

    /** Match strtol(..., base=0) for the narrow integer option we expose. */
    private fun parseCInteger(value: String): Long? {
        if (value.isEmpty()) return null
        var body = value
        var negative = false
        when (body.first()) {
            '+' -> body = body.drop(1)
            '-' -> {
                negative = true
                body = body.drop(1)
            }
        }
        if (body.isEmpty()) return null

        val (digits, radix) = when {
            body.startsWith("0x", ignoreCase = true) -> body.drop(2) to 16
            body.length > 1 && body.startsWith('0') -> body.drop(1) to 8
            else -> body to 10
        }
        if (digits.isEmpty()) return if (body == "0") 0L else null
        val parsed = digits.toLongOrNull(radix) ?: return null
        return if (negative) -parsed else parsed
    }

    /** Пробелы/переводы строк -> argv; пустые токены отбрасываются, длина ограничена. */
    fun tokenize(raw: String): List<String> =
        raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(MAX_TOKENS)
}
