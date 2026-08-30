package dev.triplet.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.triplet.app.core.DpiBackend
import dev.triplet.app.core.ProbeCredentials
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DpiBackendAuthTest {

    @Test fun byeDpiRequiresRfc1929Credentials() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credentials = ProbeCredentials("detour-test", "detour-test-password")
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val backend = DpiBackend(context)

        try {
            assertTrue(backend.start(emptyList(), port, credentials))

            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 1500
                socket.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00))
                socket.getOutputStream().flush()
                assertArrayEquals(intArrayOf(0x05, 0xff), readReply(socket))
            }

            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 1500
                offerUserPassword(socket)
                assertArrayEquals(intArrayOf(0x05, 0x02), readReply(socket))
                socket.getOutputStream().write(authPacket(credentials.username, "wrong-password"))
                socket.getOutputStream().flush()
                assertArrayEquals(intArrayOf(0x01, 0x01), readReply(socket))
            }

            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 1500
                offerUserPassword(socket)
                assertArrayEquals(intArrayOf(0x05, 0x02), readReply(socket))
                socket.getOutputStream().write(authPacket(credentials.username, credentials.password))
                socket.getOutputStream().flush()
                assertArrayEquals(intArrayOf(0x01, 0x00), readReply(socket))
            }
        } finally {
            backend.stop()
        }
    }

    private fun offerUserPassword(socket: Socket) {
        socket.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x02))
        socket.getOutputStream().flush()
    }

    private fun authPacket(username: String, password: String): ByteArray {
        val user = username.toByteArray(Charsets.UTF_8)
        val pass = password.toByteArray(Charsets.UTF_8)
        return byteArrayOf(0x01, user.size.toByte()) + user +
            byteArrayOf(pass.size.toByte()) + pass
    }

    private fun readReply(socket: Socket): IntArray {
        val input = socket.getInputStream()
        return intArrayOf(input.read(), input.read())
    }
}
