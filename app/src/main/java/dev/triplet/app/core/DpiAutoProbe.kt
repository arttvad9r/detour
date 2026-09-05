package dev.triplet.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Small built-in target groups. UI labels remain localized outside the core model. */
data class DpiDomainGroup(
    val id: String,
    val targets: List<DpiProbeTarget>,
) {
    init {
        require(id.isNotBlank()) { "domain group id is blank" }
        require(targets.isNotEmpty()) { "domain group is empty" }
        require(targets.map { it.id }.distinct().size == targets.size) { "duplicate target id" }
    }
}

object DpiDomainCatalog {
    val default: List<DpiDomainGroup> = listOf(
        DpiDomainGroup(
            id = "youtube",
            targets = listOf(
                DpiProbeTarget("youtube-web", "www.youtube.com", "youtube.com"),
                DpiProbeTarget("youtube-root", "youtube.com", "youtube.com"),
            ),
        ),
        DpiDomainGroup(
            id = "googlevideo",
            targets = listOf(
                DpiProbeTarget(
                    "googlevideo-redirector",
                    "redirector.googlevideo.com",
                    "googlevideo.com",
                ),
            ),
        ),
        DpiDomainGroup(
            id = "discord",
            targets = listOf(
                DpiProbeTarget("discord-web", "discord.com", "discord.com"),
                DpiProbeTarget("discord-gateway", "gateway.discord.gg", "discord.gg"),
            ),
        ),
        DpiDomainGroup(
            id = "telegram",
            targets = listOf(
                DpiProbeTarget("telegram-web", "telegram.org", "telegram.org"),
                DpiProbeTarget("telegram-webapp", "web.telegram.org", "telegram.org"),
            ),
        ),
    )
}

/**
 * Tracks whether a system VPN contaminated an automatic-search session.
 * Caller cancellation does not contaminate the session, but once a VPN is
 * observed the result must never be accepted even if that VPN disappears.
 */
internal class DpiAutoNetworkGuard(
    private val vpnActive: () -> Boolean,
) {
    private val contaminated = AtomicBoolean(false)

    fun markContaminated() {
        contaminated.set(true)
    }

    fun isCancelled(cancelled: () -> Boolean): Boolean {
        val callerCancelled = cancelled()
        val active = vpnActive()
        if (active) markContaminated()
        return callerCancelled || active || contaminated.get()
    }

    fun requireClean() {
        if (vpnActive()) markContaminated()
        check(!contaminated.get()) { "system VPN active during automatic DPI test" }
    }
}

internal object DpiAutoNetworkState {
    fun isVpnActive(context: Context): Boolean =
        isVpnActive(context.getSystemService(ConnectivityManager::class.java))

    fun isVpnActive(connectivity: ConnectivityManager): Boolean {
        val activeNetwork = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}

/**
 * Watches the app's default network for the whole AUTO session. Polling still
 * protects synchronous boundaries; the callback closes the gap where a VPN can
 * appear and disappear while a blocking HTTPS/TLS operation is in progress.
 */
internal class DpiAutoNetworkSession(context: Context) : AutoCloseable {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val guard = DpiAutoNetworkGuard { DpiAutoNetworkState.isVpnActive(connectivity) }
    private var registered = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                guard.markContaminated()
            }
        }
    }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
        registered = true
        try {
            guard.requireClean()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun isCancelled(cancelled: () -> Boolean): Boolean = guard.isCancelled(cancelled)

    fun requireClean() = guard.requireClean()

    override fun close() {
        if (!registered) return
        registered = false
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}

/**
 * Runs candidate strategies against a dedicated ciadpi process. The test port
 * is intentionally separate from production DPI (:10808), so testing cannot
 * replace the live backend by accident.
 */
class DpiAutoSelector(
    context: Context,
    private val port: Int = DEFAULT_PORT,
    private val timeoutMs: Int = 3500,
    private val credentials: ProbeCredentials = ProbeAuth.current(),
) {
    private val appContext = context.applicationContext
    private val backend = DpiBackend(appContext)

    /** Fast global-winner mode: abandon a candidate after its first failed probe. */
    fun searchWithBaseline(
        targets: List<DpiProbeTarget>,
        candidates: List<DpiStrategyCandidate> = DpiStrategyCatalog.searchDefault,
        attemptsPerTarget: Int = 2,
        cancelled: () -> Boolean = { false },
    ): DpiAutoSearchReport = withCleanNetwork(cancelled) { guardedCancelled ->
        DpiAutoSearchCoordinator(
            directProbe = DirectHttpsDpiProbe(timeoutMs = timeoutMs, cancelled = guardedCancelled),
            strategySearcher = DpiStrategySearcher { problematicTargets, searchCancelled ->
                searchInternal(
                    targets = problematicTargets,
                    candidates = candidates,
                    attemptsPerTarget = attemptsPerTarget,
                    cancelled = searchCancelled,
                    stopCandidateOnFailure = true,
                )
            },
        ).run(
            targets = targets,
            attemptsPerTarget = attemptsPerTarget,
            cancelled = guardedCancelled,
        )
    }

    /**
     * Exhaustive mode for per-domain planning. If one endpoint in a rule scope
     * fails direct baseline, every selected endpoint affected by that same
     * scope is tested under every candidate.
     */
    fun searchPerDomainWithBaseline(
        targets: List<DpiProbeTarget>,
        candidates: List<DpiStrategyCandidate> = DpiStrategyCatalog.searchDefault,
        attemptsPerTarget: Int = 2,
        cancelled: () -> Boolean = { false },
    ): DpiAutoSearchReport = withCleanNetwork(cancelled) { guardedCancelled ->
        DpiPerDomainSearchCoordinator(
            directProbe = DirectHttpsDpiProbe(timeoutMs = timeoutMs, cancelled = guardedCancelled),
            strategySearcher = DpiStrategySearcher { affectedTargets, searchCancelled ->
                searchInternal(
                    targets = affectedTargets,
                    candidates = candidates,
                    attemptsPerTarget = attemptsPerTarget,
                    cancelled = searchCancelled,
                    stopCandidateOnFailure = false,
                )
            },
        ).run(
            targets = targets,
            attemptsPerTarget = attemptsPerTarget,
            cancelled = guardedCancelled,
        )
    }

    fun search(
        targets: List<DpiProbeTarget>,
        candidates: List<DpiStrategyCandidate> = DpiStrategyCatalog.searchDefault,
        attemptsPerTarget: Int = 2,
        cancelled: () -> Boolean = { false },
        stopCandidateOnFailure: Boolean = true,
    ): List<DpiStrategyResult> = withCleanNetwork(cancelled) { guardedCancelled ->
        searchInternal(
            targets = targets,
            candidates = candidates,
            attemptsPerTarget = attemptsPerTarget,
            cancelled = guardedCancelled,
            stopCandidateOnFailure = stopCandidateOnFailure,
        )
    }

    private fun searchInternal(
        targets: List<DpiProbeTarget>,
        candidates: List<DpiStrategyCandidate>,
        attemptsPerTarget: Int,
        cancelled: () -> Boolean,
        stopCandidateOnFailure: Boolean,
    ): List<DpiStrategyResult> {
        val runner = DpiStrategySearchRunner(
            backend = object : DpiStrategyBackend {
                override fun start(candidate: DpiStrategyCandidate): Boolean =
                    backend.start(
                        strategyArgs = candidate.args,
                        port = port,
                        credentials = credentials,
                        cancelled = cancelled,
                    )

                override fun stop() = backend.stop()
            },
            probe = Socks5HttpsDpiProbe(
                proxyPort = port,
                timeoutMs = timeoutMs,
                credentials = credentials,
                cancelled = cancelled,
            ),
        )
        return runner.run(
            candidates = candidates,
            targets = targets,
            attemptsPerTarget = attemptsPerTarget,
            stopCandidateOnFailure = stopCandidateOnFailure,
            cancelled = cancelled,
        )
    }

    private fun <T> withCleanNetwork(
        cancelled: () -> Boolean,
        block: (guardedCancelled: () -> Boolean) -> T,
    ): T = DpiAutoNetworkSession(appContext).use { session ->
        val guardedCancelled = { session.isCancelled(cancelled) }
        block(guardedCancelled).also { session.requireClean() }
    }

    companion object {
        const val DEFAULT_PORT = 10818
    }
}

/** Direct HTTPS baseline that explicitly bypasses JVM HTTP proxy configuration. */
internal class DirectHttpsDpiProbe(
    private val timeoutMs: Int,
    private val cancelled: () -> Boolean = { false },
) : DpiTargetProbe {
    override fun probe(target: DpiProbeTarget): DpiProbeAttempt {
        if (cancelled() || Thread.currentThread().isInterrupted) return DpiProbeAttempt(false)
        val startedNs = System.nanoTime()
        var connection: HttpsURLConnection? = null
        return try {
            require(timeoutMs > 0) { "timeout must be positive" }
            connection = URL("https://${target.host}/")
                .openConnection(Proxy.NO_PROXY) as HttpsURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Detour-DPI-Probe")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Connection", "close")
            val status = connection.responseCode
            val latencyMs = (System.nanoTime() - startedNs) / 1_000_000L
            DpiProbeAttempt(success = DpiHttpPolicy.isReachable(status), latencyMs = latencyMs)
        } catch (_: Exception) {
            DpiProbeAttempt(success = false)
        } finally {
            connection?.disconnect()
        }
    }
}

/**
 * Minimal authenticated SOCKS5 client used only for local ByeDPI testing.
 * A probe succeeds after an authenticated SOCKS CONNECT, a verified TLS
 * handshake for the requested host and a syntactically valid HTTP response.
 */
internal class Socks5HttpsDpiProbe(
    private val proxyPort: Int,
    private val timeoutMs: Int,
    private val credentials: ProbeCredentials,
    private val cancelled: () -> Boolean = { false },
) : DpiTargetProbe {
    override fun probe(target: DpiProbeTarget): DpiProbeAttempt {
        if (cancelled() || Thread.currentThread().isInterrupted) return DpiProbeAttempt(false)
        val startedNs = System.nanoTime()
        return try {
            val status = request(target.host)
            val latencyMs = (System.nanoTime() - startedNs) / 1_000_000L
            DpiProbeAttempt(success = DpiHttpPolicy.isReachable(status), latencyMs = latencyMs)
        } catch (_: Exception) {
            DpiProbeAttempt(success = false)
        }
    }

    private fun request(host: String): Int {
        require(timeoutMs > 0) { "timeout must be positive" }
        val hostBytes = host.toByteArray(StandardCharsets.US_ASCII)
        require(hostBytes.size in 1..255) { "SOCKS host is too long" }

        Socket().use { raw ->
            raw.connect(InetSocketAddress("127.0.0.1", proxyPort), timeoutMs)
            raw.soTimeout = timeoutMs

            val input = raw.getInputStream()
            val output = raw.getOutputStream()

            output.write(byteArrayOf(0x05, 0x01, 0x02)) // SOCKS5, one method, username/password.
            output.flush()
            val methodReply = readExact(input, 2)
            if (methodReply[0] != 0x05.toByte() || methodReply[1] != 0x02.toByte()) {
                throw IOException("SOCKS authentication method rejected")
            }

            output.write(Socks5Wire.authRequest(credentials))
            output.flush()
            val authReply = readExact(input, 2)
            if (authReply[0] != 0x01.toByte() || authReply[1] != 0x00.toByte()) {
                throw IOException("SOCKS authentication failed")
            }

            output.write(Socks5Wire.connectRequest(host, 443))
            output.flush()
            readConnectReply(input)

            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("cancelled")

            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, 443, true) as SSLSocket
            tls.soTimeout = timeoutMs
            val parameters = tls.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            tls.sslParameters = parameters

            tls.use {
                it.startHandshake()
                val request = "GET / HTTP/1.1\r\n" +
                    "Host: $host\r\n" +
                    "User-Agent: Detour-DPI-Probe\r\n" +
                    "Accept: */*\r\n" +
                    "Connection: close\r\n\r\n"
                it.outputStream.write(request.toByteArray(StandardCharsets.ISO_8859_1))
                it.outputStream.flush()
                return readHttpStatus(it.inputStream)
            }
        }
    }

    private fun readConnectReply(input: InputStream) {
        val head = readExact(input, 4)
        if (head[0] != 0x05.toByte()) throw IOException("invalid SOCKS version")
        if (head[1] != 0x00.toByte()) throw IOException("SOCKS CONNECT failed: ${head[1].toInt() and 0xff}")
        val addressBytes = when (head[3].toInt() and 0xff) {
            0x01 -> 4
            0x03 -> readExact(input, 1)[0].toInt() and 0xff
            0x04 -> 16
            else -> throw IOException("invalid SOCKS address type")
        }
        readExact(input, addressBytes + 2) // bound address + bound port
    }

    private fun readHttpStatus(input: InputStream): Int {
        val line = readAsciiLine(input)
        return Socks5Wire.httpStatusCode(line) ?: throw IOException("invalid HTTP status")
    }

    private fun readAsciiLine(input: InputStream): String {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            if (value < 0) throw EOFException("unexpected EOF")
            if (value == '\n'.code) break
            if (value != '\r'.code) {
                if (bytes.size >= MAX_HTTP_LINE_BYTES) throw IOException("HTTP status line too long")
                bytes += value.toByte()
            }
        }
        return ByteArray(bytes.size) { bytes[it] }.toString(StandardCharsets.ISO_8859_1)
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val out = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(out, offset, size - offset)
            if (read < 0) throw EOFException("unexpected EOF")
            offset += read
        }
        return out
    }

    companion object {
        private const val MAX_HTTP_LINE_BYTES = 8 * 1024
    }
}

/** Reachability policy shared by direct and proxied HTTPS probes. */
internal object DpiHttpPolicy {
    fun isReachable(status: Int): Boolean = status in 200..499 && status != 451
}

/** Pure SOCKS/HTTP wire helpers kept visible to unit tests. */
internal object Socks5Wire {
    fun authRequest(credentials: ProbeCredentials): ByteArray {
        val username = credentials.username.toByteArray(StandardCharsets.UTF_8)
        val password = credentials.password.toByteArray(StandardCharsets.UTF_8)
        require(username.size in 1..255) { "invalid SOCKS username length" }
        require(password.size in 1..255) { "invalid SOCKS password length" }
        return byteArrayOf(0x01, username.size.toByte()) + username +
            byteArrayOf(password.size.toByte()) + password
    }

    fun connectRequest(host: String, port: Int): ByteArray {
        require(port in 1..65535) { "invalid SOCKS target port" }
        val hostBytes = host.toByteArray(StandardCharsets.US_ASCII)
        require(hostBytes.size in 1..255) { "invalid SOCKS host length" }
        return byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()) +
            hostBytes + byteArrayOf((port ushr 8).toByte(), port.toByte())
    }

    fun httpStatusCode(statusLine: String): Int? {
        val parts = statusLine.split(' ', limit = 3)
        if (parts.size < 2 || !parts[0].startsWith("HTTP/")) return null
        return parts[1].toIntOrNull()?.takeIf { it in 100..599 }
    }
}
