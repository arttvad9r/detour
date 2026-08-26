package dev.triplet.app.core

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
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

    fun start(strategyArgs: List<String>, port: Int, cancelled: () -> Boolean = { false }): Boolean {
        stop()
        if (!bin.exists()) return false
        // -U: TCP-only для DPI-маршрута (fail-closed); QUIC режется правилом
        // с быстрым отказом, приложения сразу уходят на TCP-обход.
        val cmd = listOf(bin.absolutePath, "-i", "127.0.0.1", "-p", port.toString(), "-U") +
            strategyArgs
        return try {
            stopping.set(false)
            // Обязательно уводим stdout/stderr: непрочитанный пайп заполняется
            // (~64KB) и ciadpi блокируется на записи — выглядит как «повис».
            proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start()
            val started = awaitPort(port, timeoutMs = 4000, cancelled = cancelled)
            if (started) watchExit(proc!!)
            started
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
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cancelled() || Thread.currentThread().isInterrupted) return false
            if (proc?.isAlive != true) return false
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 250)
                    s.close()
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
