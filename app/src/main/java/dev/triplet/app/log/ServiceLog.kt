package dev.triplet.app.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory ring buffer shown on the diagnostics screen. Never log secrets. */
object ServiceLog {
    private const val CAPACITY = 300
    private val buf = ArrayDeque<String>()
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun i(msg: String) = add("I $msg")
    fun w(msg: String) = add("W $msg")
    fun e(msg: String) = add("E $msg")

    @Synchronized
    private fun add(line: String) {
        buf.addLast("${ts()} $line")
        while (buf.size > CAPACITY) buf.removeFirst()
        _lines.value = buf.toList()
    }

    private fun ts(): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
}
