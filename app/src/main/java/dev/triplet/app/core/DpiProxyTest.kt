package dev.triplet.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Selectable set of real HTTPS hostnames used by the proxy test. */
data class DpiProxyTestDomainList(
    val id: String,
    val displayName: String,
    val hosts: List<String>,
    val defaultSelected: Boolean,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(hosts.isNotEmpty())
        require(hosts.distinct().size == hosts.size)
        require(hosts.all(::isSafeProbeHost))
    }
}

data class DpiProxyTestConfig(
    val attemptsPerHost: Int = DEFAULT_ATTEMPTS,
    val concurrency: Int = DEFAULT_CONCURRENCY,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
) {
    init {
        require(attemptsPerHost in ATTEMPTS_RANGE)
        require(concurrency in CONCURRENCY_RANGE)
        require(timeoutSeconds in TIMEOUT_RANGE)
    }

    companion object {
        val ATTEMPTS_RANGE = 1..20
        val CONCURRENCY_RANGE = 1..50
        val TIMEOUT_RANGE = 1..15
        const val DEFAULT_ATTEMPTS = 1
        const val DEFAULT_CONCURRENCY = 20
        const val DEFAULT_TIMEOUT_SECONDS = 5
    }
}

data class DpiProxyTestStrategy(
    val id: String,
    val referenceIndex: Int,
    val command: String,
    val args: List<String>,
) {
    init {
        require(id.isNotBlank())
        require(referenceIndex > 0)
        require(command.isNotBlank())
        require(args.isNotEmpty())
        require(args.none { it.isBlank() || it.any { c -> c.code < 0x20 || c.code == 0x7f } })
        require(args.none { token -> token in FORBIDDEN_PROCESS_OPTIONS })
    }

    companion object {
        private val FORBIDDEN_PROCESS_OPTIONS = setOf(
            "-i", "--ip", "-p", "--port", "-D", "--daemon", "-w", "--pidfile",
            "-E", "--transparent", "-J", "--socks5-auth-stdin",
        )
    }
}

data class DpiProxyTestHostResult(
    val host: String,
    val expectedAttempts: Int,
    val attempts: Int,
    val successes: Int,
    val successfulLatenciesMs: List<Long>,
) {
    init {
        require(isSafeProbeHost(host))
        require(expectedAttempts > 0)
        require(attempts in 0..expectedAttempts)
        require(successes in 0..attempts)
        require(successfulLatenciesMs.size <= successes)
        require(successfulLatenciesMs.all { it >= 0 })
    }

    val fullyWorking: Boolean
        get() = attempts == expectedAttempts && successes == expectedAttempts

    val medianLatencyMs: Long?
        get() = successfulLatenciesMs.sorted().let { values ->
            values.takeIf { it.isNotEmpty() }?.get((values.size - 1) / 2)
        }
}

data class DpiProxyTestStrategyResult(
    val strategy: DpiProxyTestStrategy,
    val backendStarted: Boolean,
    val completed: Boolean,
    val hosts: List<DpiProxyTestHostResult>,
) {
    val fullyWorkingHosts: Int get() = hosts.count { it.fullyWorking }
    val totalSuccesses: Int get() = hosts.sumOf { it.successes }
    val totalAttempts: Int get() = hosts.sumOf { it.attempts }
    val fullCoverage: Boolean
        get() = backendStarted && completed && hosts.isNotEmpty() && fullyWorkingHosts == hosts.size

    val medianLatencyMs: Long?
        get() = hosts.flatMap { it.successfulLatenciesMs }.sorted().let { values ->
            values.takeIf { it.isNotEmpty() }?.get((values.size - 1) / 2)
        }
}

data class DpiProxyTestProgress(
    val strategyIndex: Int,
    val strategyTotal: Int,
    val hostsCompleted: Int,
    val hostsTotal: Int,
) {
    init {
        require(strategyIndex in 1..strategyTotal)
        require(hostsCompleted in 0..hostsTotal)
        require(hostsTotal > 0)
    }
}

object DpiProxyTestCatalog {
    val domainLists: List<DpiProxyTestDomainList> = DpiProxyTestReferenceData.domainLists
    val strategies: List<DpiProxyTestStrategy> = DpiProxyTestReferenceData.strategies

    val defaultSelectedIds: Set<String> = domainLists
        .filter { it.defaultSelected }
        .mapTo(linkedSetOf()) { it.id }

    init {
        check(domainLists.map { it.id }.distinct().size == domainLists.size)
        check(strategies.size == 60) { "reference strategy corpus drifted" }
        check(strategies.map { it.id }.distinct().size == strategies.size)
        check(defaultSelectedIds == setOf("youtube", "googlevideo"))
    }

    fun selectedHosts(selectedIds: Set<String>): List<String> = domainLists
        .filter { it.id in selectedIds }
        .flatMap { it.hosts }
        .distinct()

    fun isTrustedCommand(raw: String): Boolean {
        val normalized = normalizeCommand(raw)
        return normalized.isNotEmpty() && strategies.any { normalizeCommand(it.command) == normalized }
    }

    internal fun normalizeCommand(raw: String): String = raw.trim().split(Regex("\\s+")).joinToString(" ")
}

object DpiProxyTestRanker {
    fun rank(results: List<DpiProxyTestStrategyResult>): List<DpiProxyTestStrategyResult> =
        results.sortedWith(
            compareByDescending<DpiProxyTestStrategyResult> { it.backendStarted && it.completed }
                .thenByDescending { it.fullCoverage }
                .thenByDescending { it.fullyWorkingHosts }
                .thenByDescending { it.totalSuccesses }
                .thenBy { it.medianLatencyMs ?: Long.MAX_VALUE }
                .thenBy { it.strategy.referenceIndex },
        )
}

/**
 * Phone-oriented diagnostic runner. Strategies are sequential; host checks for
 * the current strategy use bounded concurrency. A selected attempt count is
 * always honored for a host unless the user cancels the whole test.
 */
class DpiProxyTester(
    context: Context,
    private val port: Int = DEFAULT_PORT,
    private val credentials: ProbeCredentials = ProbeAuth.current(),
) {
    private val appContext = context.applicationContext
    private val backend = DpiBackend(appContext)

    suspend fun run(
        selectedIds: Set<String>,
        config: DpiProxyTestConfig,
        strategies: List<DpiProxyTestStrategy> = DpiProxyTestCatalog.strategies,
        onProgress: (DpiProxyTestProgress) -> Unit = {},
    ): List<DpiProxyTestStrategyResult> {
        val hosts = DpiProxyTestCatalog.selectedHosts(selectedIds)
        require(hosts.isNotEmpty()) { "no proxy-test domains selected" }
        require(strategies.isNotEmpty()) { "no proxy-test strategies selected" }
        require(strategies.map { it.id }.distinct().size == strategies.size) {
            "duplicate proxy-test strategy ids"
        }

        return DpiProxyTestNetworkSession(appContext).use { session ->
            val results = mutableListOf<DpiProxyTestStrategyResult>()
            for ((strategyOffset, strategy) in strategies.withIndex()) {
                currentCoroutineContext().ensureActive()
                session.requireClean()
                val coroutineContext = currentCoroutineContext()
                val cancelled = { !coroutineContext.isActive || session.isContaminated() }
                val started = backend.start(
                    strategyArgs = strategy.args,
                    port = port,
                    credentials = credentials,
                    cancelled = cancelled,
                )
                if (!started) {
                    results += DpiProxyTestStrategyResult(
                        strategy = strategy,
                        backendStarted = false,
                        completed = true,
                        hosts = hosts.map {
                            DpiProxyTestHostResult(it, config.attemptsPerHost, 0, 0, emptyList())
                        },
                    )
                    continue
                }

                try {
                    val hostResults = runHosts(
                        hosts = hosts,
                        config = config,
                        probe = AuthenticatedSocksHttpsProbe(
                            proxyPort = port,
                            timeoutMs = config.timeoutSeconds * 1_000,
                            credentials = credentials,
                            cancelled = cancelled,
                        ),
                        onHostComplete = { completed ->
                            onProgress(
                                DpiProxyTestProgress(
                                    strategyIndex = strategyOffset + 1,
                                    strategyTotal = strategies.size,
                                    hostsCompleted = completed,
                                    hostsTotal = hosts.size,
                                ),
                            )
                        },
                    )
                    session.requireClean()
                    results += DpiProxyTestStrategyResult(
                        strategy = strategy,
                        backendStarted = true,
                        completed = true,
                        hosts = hostResults,
                    )
                } finally {
                    backend.stop()
                }
            }
            DpiProxyTestRanker.rank(results)
        }
    }

    private suspend fun runHosts(
        hosts: List<String>,
        config: DpiProxyTestConfig,
        probe: AuthenticatedSocksHttpsProbe,
        onHostComplete: (Int) -> Unit,
    ): List<DpiProxyTestHostResult> = coroutineScope {
        val semaphore = Semaphore(config.concurrency)
        val completed = AtomicInteger(0)
        hosts.map { host ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    var attempts = 0
                    var successes = 0
                    val latencies = mutableListOf<Long>()
                    repeat(config.attemptsPerHost) {
                        currentCoroutineContext().ensureActive()
                        attempts++
                        val observation = probe.probe(host)
                        if (observation.success) {
                            successes++
                            observation.latencyMs?.let(latencies::add)
                        }
                    }
                    DpiProxyTestHostResult(
                        host = host,
                        expectedAttempts = config.attemptsPerHost,
                        attempts = attempts,
                        successes = successes,
                        successfulLatenciesMs = latencies,
                    ).also { onHostComplete(completed.incrementAndGet()) }
                }
            }
        }.awaitAll()
    }

    companion object {
        const val DEFAULT_PORT = 10818
    }
}

internal data class DpiProxyObservation(val success: Boolean, val latencyMs: Long?)

/** Verified TLS/HTTP probe through the authenticated local ByeDPI SOCKS server. */
internal class AuthenticatedSocksHttpsProbe(
    private val proxyPort: Int,
    private val timeoutMs: Int,
    private val credentials: ProbeCredentials,
    private val cancelled: () -> Boolean,
) {
    init {
        require(proxyPort in 1..65535)
        require(timeoutMs > 0)
    }

    suspend fun probe(host: String): DpiProxyObservation {
        currentCoroutineContext().ensureActive()
        if (isCancelled()) throw CancellationException("proxy test cancelled")
        val startedNs = System.nanoTime()
        return try {
            val status = withTimeoutOrNull(timeoutMs.toLong()) { request(host) }
                ?: return DpiProxyObservation(success = false, latencyMs = null)
            DpiProxyObservation(
                success = DpiProxyHttpPolicy.isReachable(status),
                latencyMs = (System.nanoTime() - startedNs) / 1_000_000L,
            )
        } catch (cancelledError: CancellationException) {
            throw cancelledError
        } catch (_: Exception) {
            DpiProxyObservation(success = false, latencyMs = null)
        }
    }

    private suspend fun request(host: String): Int = coroutineScope {
        require(isSafeProbeHost(host))
        val raw = Socket()
        val cancellationWatcher = launch(Dispatchers.Default) {
            while (isActive) {
                if (cancelled()) {
                    runCatching { raw.close() }
                    return@launch
                }
                delay(CANCELLATION_POLL_MS)
            }
        }
        try {
            suspendCancellableCoroutine<Int> { continuation ->
                continuation.invokeOnCancellation { runCatching { raw.close() } }
                try {
                    val status = requestBlocking(host, raw)
                    if (isCancelled()) {
                        continuation.cancel(CancellationException("proxy test cancelled"))
                    } else if (continuation.isActive) {
                        continuation.resumeWith(Result.success(status))
                    }
                } catch (error: Exception) {
                    if (isCancelled()) {
                        continuation.cancel(CancellationException("proxy test cancelled"))
                    } else if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(error))
                    }
                }
            }
        } finally {
            cancellationWatcher.cancel()
            runCatching { raw.close() }
        }
    }

    private fun requestBlocking(host: String, raw: Socket): Int {
        raw.connect(InetSocketAddress("127.0.0.1", proxyPort), timeoutMs)
        raw.soTimeout = timeoutMs
        val input = raw.getInputStream()
        val output = raw.getOutputStream()

        output.write(byteArrayOf(0x05, 0x01, 0x02))
        output.flush()
        val method = readExact(input, 2)
        if (method[0] != 0x05.toByte() || method[1] != 0x02.toByte()) {
            throw IOException("SOCKS auth method rejected")
        }
        if (isCancelled()) throw CancellationException("proxy test cancelled")

        output.write(DpiProxySocksWire.authRequest(credentials))
        output.flush()
        val auth = readExact(input, 2)
        if (auth[0] != 0x01.toByte() || auth[1] != 0x00.toByte()) {
            throw IOException("SOCKS authentication failed")
        }
        if (isCancelled()) throw CancellationException("proxy test cancelled")

        output.write(DpiProxySocksWire.connectRequest(host, 443))
        output.flush()
        readConnectReply(input)
        if (isCancelled()) throw CancellationException("proxy test cancelled")

        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(raw, host, 443, true) as SSLSocket
        tls.soTimeout = timeoutMs
        tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        tls.use {
            it.startHandshake()
            if (isCancelled()) throw CancellationException("proxy test cancelled")
            val request = "GET / HTTP/1.1\r\nHost: $host\r\nUser-Agent: Detour-DPI-Probe\r\n" +
                "Accept: */*\r\nConnection: close\r\n\r\n"
            it.outputStream.write(request.toByteArray(StandardCharsets.ISO_8859_1))
            it.outputStream.flush()
            return DpiProxySocksWire.httpStatusCode(readAsciiLine(it.inputStream))
                ?: throw IOException("invalid HTTP status")
        }
    }

    private fun isCancelled(): Boolean = cancelled() || Thread.currentThread().isInterrupted

    private fun readConnectReply(input: InputStream) {
        val head = readExact(input, 4)
        if (head[0] != 0x05.toByte() || head[1] != 0x00.toByte()) throw IOException("SOCKS CONNECT failed")
        val addressSize = when (head[3].toInt() and 0xff) {
            0x01 -> 4
            0x03 -> readExact(input, 1)[0].toInt() and 0xff
            0x04 -> 16
            else -> throw IOException("invalid SOCKS address type")
        }
        readExact(input, addressSize + 2)
    }

    private fun readAsciiLine(input: InputStream): String {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            if (value < 0) throw EOFException("unexpected EOF")
            if (value == '\n'.code) break
            if (value != '\r'.code) {
                if (bytes.size >= 8 * 1024) throw IOException("HTTP status line too long")
                bytes += value.toByte()
            }
        }
        return ByteArray(bytes.size) { bytes[it] }.toString(StandardCharsets.ISO_8859_1)
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val out = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(out, offset, size - offset)
            if (count < 0) throw EOFException("unexpected EOF")
            offset += count
        }
        return out
    }

    companion object {
        private const val CANCELLATION_POLL_MS = 50L
    }
}

internal object DpiProxyHttpPolicy {
    fun isReachable(status: Int): Boolean = status in 200..499 && status != 444 && status != 451
}

internal object DpiProxySocksWire {
    fun authRequest(credentials: ProbeCredentials): ByteArray {
        val user = credentials.username.toByteArray(StandardCharsets.UTF_8)
        val pass = credentials.password.toByteArray(StandardCharsets.UTF_8)
        require(user.size in 1..255 && pass.size in 1..255)
        return byteArrayOf(0x01, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass
    }

    fun connectRequest(host: String, port: Int): ByteArray {
        require(isSafeProbeHost(host))
        require(port in 1..65535)
        val bytes = host.toByteArray(StandardCharsets.US_ASCII)
        require(bytes.size in 1..255)
        return byteArrayOf(0x05, 0x01, 0x00, 0x03, bytes.size.toByte()) + bytes +
            byteArrayOf((port ushr 8).toByte(), port.toByte())
    }

    fun httpStatusCode(line: String): Int? {
        val parts = line.split(' ', limit = 3)
        if (parts.size < 2 || !parts[0].startsWith("HTTP/")) return null
        return parts[1].toIntOrNull()?.takeIf { it in 100..599 }
    }
}

internal class DpiProxyTestNetworkSession(context: Context) : AutoCloseable {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val contaminated = AtomicBoolean(false)
    private var registered = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) contaminated.set(true)
        }
    }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
        registered = true
        try {
            requireClean()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun isContaminated(): Boolean {
        if (isVpnActive()) contaminated.set(true)
        return contaminated.get()
    }

    fun requireClean() {
        check(!isContaminated()) { "system VPN active during DPI proxy test" }
    }

    private fun isVpnActive(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    override fun close() {
        if (!registered) return
        registered = false
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}

internal fun isSafeProbeHost(host: String): Boolean = host.isNotBlank() &&
    host.length <= 253 &&
    host.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f || it == '/' || it == ':' }
