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
        // -U: TCP-only for the DPI route (fail-closed); QUIC is rejected by the
        // mihomo rule so applications immediately retry over TCP. --auth-stdin
        // keeps the internal SOCKS hop inaccessible to unrelated local apps.
        val cmd = listOf(
            bin.absolutePath,
            "-i", "127.0.0.1",
            "-p", port.toString(),
            "-U",
            "--auth-stdin",
        ) + strategyArgs
        return try {
            stopping.set(false)
            // Always drain stdout/stderr: an unread pipe fills (~64KB) and blocks
            // ciadpi on write, which otherwise looks like a hung proxy.
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start()
            proc = process

            // Credentials are process-ephemeral and never appear in argv or the
            // environment. Closing stdin after the two lines prevents later data
            // from being interpreted as credentials by the child.
            val credentials = ProbeAuth.current()
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.append(credentials.username).append('\n')
                writer.append(credentials.password).append('\n')
            }

            val started = awaitPort(port, timeoutMs = 4000, cancelled = cancelled)
            if (started) watchExit(process)
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
