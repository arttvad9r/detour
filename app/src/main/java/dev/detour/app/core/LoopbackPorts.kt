package dev.detour.app.core

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Picks free loopback TCP ports for Detour's internal listeners (ByeDPI SOCKS
 * and the engine's authenticated probe inbounds).
 *
 * A fixed port breaks the tunnel whenever another app already listens on it.
 * The listeners are authenticated and loopback-only, so any free port works.
 */
object LoopbackPorts {
    private const val MAX_COUNT = 16
    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")

    /**
     * Returns [count] distinct free ports. Every candidate socket stays open
     * until all ports are chosen, so one call never returns duplicates.
     *
     * The ports are released before returning, so another process can still take
     * one before its real owner binds it. Callers treat a failed bind as a
     * startup failure.
     *
     * @throws IOException if the system cannot open a loopback socket.
     */
    @Throws(IOException::class)
    fun allocate(count: Int): List<Int> {
        require(count in 1..MAX_COUNT) { "count must be in 1..$MAX_COUNT" }
        val sockets = ArrayList<ServerSocket>(count)
        try {
            repeat(count) { sockets += ServerSocket(0, 1, loopback) }
            return sockets.map { it.localPort }
        } finally {
            sockets.forEach { runCatching { it.close() } }
        }
    }
}
