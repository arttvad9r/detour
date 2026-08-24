package dev.triplet.app.core

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Runs the packaged ciadpi binary as a child process bound to loopback.
 * Lifecycle equals the VPN service lifecycle (start before mihomo, stop after).
 */
class DpiBackend(context: Context) {

    private val bin = File(context.applicationInfo.nativeLibraryDir, "libciadpi.so")
    private var proc: Process? = null

    fun start(preset: DpiPreset, port: Int): Boolean {
        stop()
        if (!bin.exists()) return false
        val cmd = listOf(bin.absolutePath, "-i", "127.0.0.1", "-p", port.toString(), "-U") +
            preset.args
        return try {
            proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            awaitPort(port, timeoutMs = 4000)
        } catch (e: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        proc?.destroy()
        proc = null
    }

    fun isRunning(): Boolean = proc?.isAlive == true

    private fun awaitPort(port: Int, timeoutMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
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
