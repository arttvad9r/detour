package dev.triplet.app.vpn

import dev.triplet.app.core.ProbeAuth
import dev.triplet.app.core.ProbeCredentials
import dev.triplet.app.log.ServiceLog
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** End-to-end probe through the engine's authenticated loopback mixed inbound. */
object HealthCheck {
    private const val MAX_HTTP_LINE_BYTES = 8 * 1024
    private const val MAX_HTTP_HEADERS = 100
    private const val COLD_ROUTE_RETRY_DELAY_MS = 300L

    private val endpoints = listOf(
        "https://www.gstatic.com/generate_204",
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
    )

    fun generate204(
        proxyPort: Int,
        timeoutMs: Int = 2500,
        cancelled: () -> Boolean = { false },
        credentials: ProbeCredentials = ProbeAuth.current(),
    ): Boolean {
        // Route validation runs after the UI is already Active. A short backoff
        // between attempts gives a freshly restarted VLESS adapter time to finish
        // cold DNS/TLS setup without delaying a route that succeeds immediately.
        return retry(
            endpoints = endpoints,
            attempts = 2,
            retryDelayMs = COLD_ROUTE_RETRY_DELAY_MS,
            cancelled = cancelled,
        ) { endpoint ->
            try {
                val code = requestThroughAuthenticatedProxy(endpoint, proxyPort, timeoutMs, credentials)
                val ok = code == 204
                if (!ok) ServiceLog.i("probe :$proxyPort $endpoint -> HTTP $code")
                ok
            } catch (e: Exception) {
                if (!cancelled()) ServiceLog.i("probe :$proxyPort $endpoint -> ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }
    }

    private fun requestThroughAuthenticatedProxy(
        endpoint: String,
        proxyPort: Int,
        timeoutMs: Int,
        credentials: ProbeCredentials,
    ): Int {
        val url = URL(endpoint)
        require(url.protocol == "https") { "health endpoint must use HTTPS" }
        val targetPort = if (url.port >= 0) url.port else url.defaultPort
        val host = url.host

        Socket().use { raw ->
            raw.connect(InetSocketAddress("127.0.0.1", proxyPort), timeoutMs)
            raw.soTimeout = timeoutMs
            raw.getOutputStream().apply {
                write(proxyConnectRequest(host, targetPort, credentials).toByteArray(StandardCharsets.ISO_8859_1))
                flush()
            }
            val connectCode = readResponseHead(raw.getInputStream())
            if (connectCode != 200) throw IOException("proxy CONNECT returned HTTP $connectCode")

            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, targetPort, true) as SSLSocket
            tls.soTimeout = timeoutMs
            val parameters = tls.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            tls.sslParameters = parameters

            tls.use {
                it.startHandshake()
                val requestTarget = url.file.ifBlank { "/" }
                val hostHeader = if (targetPort == 443) host else "$host:$targetPort"
                val request = "GET $requestTarget HTTP/1.1\r\n" +
                    "Host: $hostHeader\r\n" +
                    "User-Agent: Detour-health\r\n" +
                    "Accept: */*\r\n" +
                    "Connection: close\r\n\r\n"
                it.outputStream.apply {
                    write(request.toByteArray(StandardCharsets.ISO_8859_1))
                    flush()
                }
                return readResponseHead(it.inputStream)
            }
        }
    }

    internal fun proxyConnectRequest(
        host: String,
        port: Int,
        credentials: ProbeCredentials,
    ): String {
        val token = Base64.getEncoder().encodeToString(
            "${credentials.username}:${credentials.password}".toByteArray(StandardCharsets.ISO_8859_1),
        )
        return "CONNECT $host:$port HTTP/1.1\r\n" +
            "Host: $host:$port\r\n" +
            "Proxy-Authorization: Basic $token\r\n" +
            "Proxy-Connection: Keep-Alive\r\n\r\n"
    }

    internal fun statusCode(statusLine: String): Int? {
        val parts = statusLine.split(' ', limit = 3)
        if (parts.size < 2 || !parts[0].startsWith("HTTP/")) return null
        return parts[1].toIntOrNull()?.takeIf { it in 100..599 }
    }

    private fun readResponseHead(input: InputStream): Int {
        val status = statusCode(readAsciiLine(input))
            ?: throw IOException("invalid HTTP status line")
        repeat(MAX_HTTP_HEADERS) {
            if (readAsciiLine(input).isEmpty()) return status
        }
        throw IOException("too many HTTP headers")
    }

    private fun readAsciiLine(input: InputStream): String {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            if (value < 0) throw EOFException("unexpected EOF in HTTP headers")
            if (value == '\n'.code) break
            if (value != '\r'.code) {
                if (bytes.size >= MAX_HTTP_LINE_BYTES) throw IOException("HTTP header line too long")
                bytes += value.toByte()
            }
        }
        return ByteArray(bytes.size) { bytes[it] }.toString(StandardCharsets.ISO_8859_1)
    }

    internal fun retry(
        endpoints: List<String>,
        attempts: Int,
        retryDelayMs: Long = 0L,
        cancelled: () -> Boolean = { false },
        check: (String) -> Boolean,
    ): Boolean {
        for (endpoint in endpoints) {
            repeat(attempts) { attempt ->
                if (cancelled() || Thread.currentThread().isInterrupted) return false
                if (check(endpoint)) return true
                if (attempt < attempts - 1 && retryDelayMs > 0L) {
                    try {
                        Thread.sleep(retryDelayMs)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
            }
        }
        return false
    }
}
