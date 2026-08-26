package dev.triplet.app.vpn

import dev.triplet.app.log.ServiceLog
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** End-to-end probe through the engine's loopback mixed inbound. */
object HealthCheck {
    private val endpoints = listOf(
        "https://www.gstatic.com/generate_204",
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
    )

    fun generate204(proxyPort: Int, timeoutMs: Int = 5000, cancelled: () -> Boolean = { false }): Boolean {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
        return retry(endpoints, attempts = 2, cancelled = cancelled) { endpoint ->
            try {
                val conn = URL(endpoint).openConnection(proxy) as HttpsURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                try {
                    val ok = conn.responseCode == 204
                    if (!ok) ServiceLog.i("probe :$proxyPort $endpoint -> HTTP ${conn.responseCode}")
                    ok
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                if (!cancelled()) ServiceLog.i("probe :$proxyPort $endpoint -> ${e.javaClass.simpleName}: ${e.message}")
                false
            }
                }
    }

    internal fun retry(
        endpoints: List<String>, attempts: Int, cancelled: () -> Boolean = { false },
        check: (String) -> Boolean,
    ): Boolean {
        for (endpoint in endpoints) repeat(attempts) {
            if (cancelled() || Thread.currentThread().isInterrupted) return false
            if (check(endpoint)) return true
        }
        return false
    }
}
