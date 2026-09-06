package dev.triplet.app.core

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the packaged ciadpi binary as a child process bound to loopback.
 * Lifecycle equals the VPN service lifecycle (start before mihomo, stop after).
 */
class DpiBackend(context: Context, private val onUnexpectedExit: () -> Unit = {}) {

    private val bin = File(context.applicationInfo.nativeLibraryDir, "libciadpi.so")
    private var proc: Process? = null
    private val stopping = AtomicBoolean(false)

    fun start(
        strategyArgs: List<String>,
        port: Int,
        credentials: ProbeCredentials = ProbeAuth.current(),
        cancelled: () -> Boolean = { false },
    ): Boolean {
        stop()
        if (!bin.exists()) return false
        if (!validCredential(credentials.username) || !validCredential(credentials.password)) return false
        // -U: TCP-only для DPI-маршрута (fail-closed); QUIC режется правилом
        // с быстрым отказом, приложения сразу уходят на TCP-обход.
        // -J: Detour's pinned ByeDPI transform requires RFC1929 credentials on stdin.
        val cmd = listOf(
            bin.absolutePath, "-i", "127.0.0.1", "-p", port.toString(), "-U", "-J",
        ) + strategyArgs
        return try {
            stopping.set(false)
            // Обязательно уводим stdout/stderr: непрочитанный пайп заполняется
            // (~64KB) и ciadpi блокируется на записи — выглядит как «повис».
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start()
            proc = process
            // Credentials are process-ephemeral and never enter argv, environment,
            // logs, or persistent settings. The child reads exactly these two lines
            // before opening the SOCKS listener.
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.append(credentials.username).append('\n')
                writer.append(credentials.password).append('\n')
            }
            val started = awaitPort(port, timeoutMs = 4000, cancelled = cancelled)
            if (started) {
                watchExit(process)
            } else {
                // A cancelled/timed-out startup can leave a perfectly healthy child
                // running before its listener becomes observable. Failed start is a
                // strict lifecycle boundary: never return with a retained process.
                val startCancelled = cancelled() || Thread.currentThread().isInterrupted
                stop()
                if (startCancelled) throw CancellationException("DPI backend start cancelled")
            }
            started
        } catch (cancelledError: CancellationException) {
            stop()
            throw cancelledError
        } catch (e: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        val old = proc ?: return
        stopping.set(true)
        try {
            old.destroy()
            if (!old.waitFor(750, TimeUnit.MILLISECONDS)) {
                old.destroyForcibly()
                old.waitFor(750, TimeUnit.MILLISECONDS)
            }
        } catch (e: InterruptedException) {
            old.destroyForcibly()
            Thread.currentThread().interrupt()
        } finally {
            proc = null
        }
    }

    fun isAlive(): Boolean = proc?.isAlive == true

    private fun validCredential(value: String): Boolean {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return bytes.size in 1..255 && value.none { it == '\r' || it == '\n' || it == '\u0000' }
    }

    private fun watchExit(process: Process) {
        Thread {
            try {
                process.waitFor()
                if (!stopping.get() && proc === process) onUnexpectedExit()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun awaitPort(port: Int, timeoutMs: Int, cancelled: () -> Boolean): Boolean {
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())
        while (System.nanoTime() < deadlineNs) {
            if (cancelled() || Thread.currentThread().isInterrupted) return false
            if (proc?.isAlive != true) return false
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 250)
                    return true
                }
            } catch (e: Exception) {
                try {
                    Thread.sleep(120)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return false
    }
}
