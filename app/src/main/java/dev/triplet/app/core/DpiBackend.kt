package dev.triplet.app.core

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Runs the packaged ciadpi binary as a child process bound to loopback.
 * Lifecycle equals the VPN service lifecycle (start before mihomo, stop after).
 */
class DpiBackend(context: Context) {

    private val bin = File(context.applicationInfo.nativeLibraryDir, "libciadpi.so")
    private var proc: Process? = null

    fun start(strategyArgs: List<String>, port: Int): Boolean {
        stop()
        if (!bin.exists()) return false
        // -U: TCP-only для DPI-маршрута (fail-closed); QUIC режется правилом
        // с быстрым отказом, приложения сразу уходят на TCP-обход.
        val cmd = listOf(bin.absolutePath, "-i", "127.0.0.1", "-p", port.toString(), "-U") +
            strategyArgs
        return try {
            // Обязательно уводим stdout/stderr: непрочитанный пайп заполняется
            // (~64KB) и ciadpi блокируется на записи — выглядит как «повис».
            proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start()
            awaitPort(port, timeoutMs = 4000)
        } catch (e: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        val old = proc ?: return
        old.destroy()
        if (!old.waitFor(750, TimeUnit.MILLISECONDS)) {
            old.destroyForcibly()
            old.waitFor(750, TimeUnit.MILLISECONDS)
        }
        proc = null
    }

    private fun awaitPort(port: Int, timeoutMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (proc?.isAlive != true) return false
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 250)
                    s.close()
                    return true
                }
            } catch (e: Exception) {
                Thread.sleep(120)
            }
        }
        return false
    }
}
