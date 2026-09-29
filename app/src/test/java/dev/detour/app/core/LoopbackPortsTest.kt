package dev.detour.app.core

import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopbackPortsTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Test fun `returns the requested number of distinct unprivileged ports`() {
        val ports = LoopbackPorts.allocate(3)

        assertEquals(3, ports.size)
        assertEquals(3, ports.toSet().size)
        assertTrue(ports.all { it in 1024..65535 })
    }

    @Test fun `allocated ports are released and can be bound`() {
        val ports = LoopbackPorts.allocate(3)

        ports.forEach { port ->
            ServerSocket(port, 1, loopback).use { assertEquals(port, it.localPort) }
        }
    }

    @Test fun `does not return a port that is already in use`() {
        ServerSocket(0, 1, loopback).use { busy ->
            repeat(20) {
                assertFalse(LoopbackPorts.allocate(8).contains(busy.localPort))
            }
        }
    }

    @Test fun `rejects counts outside the supported range`() {
        assertThrows(IllegalArgumentException::class.java) { LoopbackPorts.allocate(0) }
        assertThrows(IllegalArgumentException::class.java) { LoopbackPorts.allocate(17) }
    }
}
