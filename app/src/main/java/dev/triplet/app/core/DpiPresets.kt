package dev.triplet.app.core

/**
 * ByeDPI strategy presets.
 * RECOMMENDED — лестница, подобранная и проверенная на сети провайдера
 * владельца (МТС Вологда, приёмка 2026-08-24); CUSTOM — пользовательская
 * стратегия из безопасного набора desync-параметров закреплённого ByeDPI.
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
    private const val MAX_RAW_LENGTH = 8 * 1024
    private const val MAX_TOKEN_LENGTH = 1024
    private const val UINT_MAX = 0xffff_ffffL
    private val decimalSeconds = Regex(
        """[+-]?(?:(?:\d+(?:\.\d*)?)|(?:\.\d+))(?:[eE][+-]?\d+)?""",
    )

    /*
     * Keep CUSTOM useful for real ByeDPI strategies while preserving Detour's
     * process boundary. Only desync/strategy options from the pinned ByeDPI CLI
     * are accepted here. Listener, daemon, filesystem, debug, auth and explicit
     * destination/bind controls remain owned by Detour and cannot be overridden.
     */
    private val shortWithValue = setOf(
        'T', 'A', 'L', 'K', 'V', 'R', 's', 'd', 'o', 'q', 'f', 'n', 't', 'O', 'Q',
        'e', 'M', 'r', 'm', 'a', 'g', 'B',
    )
    private val shortFlags = setOf('F', 'S', 'Y', 'Z')
    private val longWithValue = setOf(
        "timeout", "auto", "auto-mode", "proto", "pf", "round", "split", "disorder",
        "oob", "disoob", "fake", "fake-sni", "ttl", "fake-offset", "fake-tls-mod",
        "oob-data", "mod-http", "tlsrec", "tlsminor", "udp-fake", "def-ttl", "copy",
    )
    private val longFlags = setOf("tfo", "md5sig", "drop-sack", "wait-send")

    fun resolve(preset: DpiPreset, customRaw: String): List<String> =
        if (preset == DpiPreset.CUSTOM) tokenize(customRaw) else preset.args

    fun isValid(raw: String): Boolean {
        if (raw.length > MAX_RAW_LENGTH) return false
        val allTokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (allTokens.isEmpty() || allTokens.size > MAX_TOKENS) return false
        if (allTokens.any { token ->
                token.length > MAX_TOKEN_LENGTH ||
                    token.any { c -> c.code < 0x20 || c.code == 0x7f }
            }
        ) return false

        var index = 0
        while (index < allTokens.size) {
            val token = allTokens[index]
            when {
                token.startsWith("--") -> {
                    val body = token.drop(2)
                    if (body.isEmpty()) return false
                    val separator = body.indexOf('=')
                    val name = if (separator >= 0) body.substring(0, separator) else body
                    val attached = if (separator >= 0) body.substring(separator + 1) else null
                    when (name) {
                        in longFlags -> if (attached != null) return false
                        in longWithValue -> {
                            val value = attached ?: allTokens.getOrNull(++index) ?: return false
                            if (!isValueValid(name, value)) return false
                        }
                        else -> return false
                    }
                }
                token.startsWith('-') && token.length >= 2 -> {
                    val option = token[1]
                    val attached = token.substring(2).takeIf { it.isNotEmpty() }
                    when (option) {
                        in shortFlags -> if (attached != null) return false
                        in shortWithValue -> {
                            val value = attached ?: allTokens.getOrNull(++index) ?: return false
                            if (!isValueValid(option.toString(), value)) return false
                        }
                        else -> return false
                    }
                }
                else -> return false
            }
            index++
        }
        return true
    }

    private fun isValueValid(option: String, value: String): Boolean {
        if (!isSafeValue(value)) return false
        return when (option) {
            "a", "udp-fake" -> parseCInteger(value)?.let { it in 0..Int.MAX_VALUE.toLong() } == true
            "T", "timeout" -> {
                if (!decimalSeconds.matches(value)) false
                else value.toFloatOrNull()?.let { seconds ->
                    val millis = seconds * 1000f
                    seconds.isFinite() && millis.isFinite() && millis.toLong() in 1..UINT_MAX
                } == true
            }
            else -> true
        }
    }

    private fun isSafeValue(value: String): Boolean = value.isNotEmpty() &&
        value.length <= MAX_TOKEN_LENGTH &&
        value.none { it.code < 0x20 || it.code == 0x7f }

    /** Match strtol(..., base=0) for the narrow integer option we validate numerically. */
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
