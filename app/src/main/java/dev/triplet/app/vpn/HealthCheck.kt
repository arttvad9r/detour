package dev.triplet.app.vpn

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** End-to-end probe through the engine's loopback mixed inbound. */
object HealthCheck {
    fun generate204(proxyPort: Int, timeoutMs: Int = 5000): Boolean = runCatching {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
        val conn = URL("https://www.gstatic.com/generate_204").openConnection(proxy) as HttpsURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        try {
            conn.responseCode == 204
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)
}
