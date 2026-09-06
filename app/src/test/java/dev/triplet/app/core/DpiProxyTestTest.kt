package dev.triplet.app.core

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiProxyTestTest {
    @Test fun `reference defaults are youtube and googlevideo only`() {
        assertEquals(setOf("youtube", "googlevideo"), DpiProxyTestCatalog.defaultSelectedIds)
        assertEquals(13, DpiProxyTestCatalog.domainLists.single { it.id == "youtube" }.hosts.size)
        assertEquals(19, DpiProxyTestCatalog.domainLists.single { it.id == "googlevideo" }.hosts.size)
        assertEquals(32, DpiProxyTestCatalog.selectedHosts(DpiProxyTestCatalog.defaultSelectedIds).size)
    }

    @Test fun `reference corpus exposes sixty trusted strategies`() {
        assertEquals(60, DpiProxyTestCatalog.strategies.size)
        val first = DpiProxyTestCatalog.strategies.first()
        assertTrue(DpiProxyTestCatalog.isTrustedCommand(first.command))
        assertTrue(DpiProxyTestCatalog.isTrustedCommand("  ${first.command.replace(" ", "   ")}  "))
        assertFalse(DpiProxyTestCatalog.isTrustedCommand(first.command + " -p 9999"))
        assertTrue(DpiProxyTestCatalog.strategies.all { DpiArgs.isValid(it.command) })
    }

    @Test fun `safe custom grammar accepts real desync options and blocks process controls`() {
        assertTrue(DpiArgs.isValid("-n google.com -Qr -f-1 -r1+s -At,r,s -a1"))
        assertTrue(DpiArgs.isValid("--fake -1 --ttl 8 --split 1+s --disorder 3+s -a1"))
        assertFalse(DpiArgs.isValid("-p1080 -d1"))
        assertFalse(DpiArgs.isValid("--port=1080 -d1"))
        assertFalse(DpiArgs.isValid("-i 0.0.0.0 -d1"))
        assertFalse(DpiArgs.isValid("--connect-to 1.1.1.1:443 -d1"))
        assertFalse(DpiArgs.isValid("--fake-data /sdcard/payload.bin -d1"))
        assertFalse(DpiArgs.isValid("--unknown value"))
    }

    @Test fun `exact reference command is valid custom but edited reference process control is rejected`() {
        val strategy = DpiProxyTestCatalog.strategies.first()
        assertTrue(DpiArgs.isValid(strategy.command))
        assertEquals(strategy.args, DpiArgs.resolve(DpiPreset.CUSTOM, strategy.command))
        assertFalse(DpiArgs.isValid(strategy.command + " -p 9999"))
    }

    @Test fun `custom strategy can be appended to selected references`() {
        val reference = DpiProxyTestCatalog.strategies.first()
        val customRaw = "-n google.com -Qr -f-1 -r1+s -a1"
        val custom = DpiProxyTestStrategySelection.custom(customRaw)
        assertTrue(custom != null)
        assertEquals(DpiProxyTestStrategySelection.CUSTOM_STRATEGY_ID, custom?.id)
        assertEquals(customRaw, custom?.command)

        val selected = DpiProxyTestStrategySelection.build(setOf(reference.id), customRaw)
        assertEquals(2, selected.size)
        assertEquals(reference, selected[0])
        assertEquals(DpiProxyTestStrategySelection.CUSTOM_STRATEGY_ID, selected[1].id)
    }

    @Test fun `invalid custom strategy is not admitted`() {
        assertEquals(null, DpiProxyTestStrategySelection.custom("-p 9999"))
        assertTrue(DpiProxyTestStrategySelection.build(emptySet(), "-p 9999").isEmpty())
    }

    @Test fun `host is fully working only after every configured attempt`() {
        val partial = DpiProxyTestHostResult(
            host = "youtube.com",
            expectedAttempts = 2,
            attempts = 1,
            successes = 1,
            successfulLatenciesMs = listOf(10),
        )
        val complete = partial.copy(attempts = 2, successes = 2, successfulLatenciesMs = listOf(10, 11))
        assertFalse(partial.fullyWorking)
        assertTrue(complete.fullyWorking)
    }

    @Test fun `ranker prefers full coverage over lower latency partial result`() {
        val strategyA = DpiProxyTestStrategy("a", 1, "-d1", listOf("-d1"))
        val strategyB = DpiProxyTestStrategy("b", 2, "-s1", listOf("-s1"))
        val full = DpiProxyTestStrategyResult(
            strategyA,
            backendStarted = true,
            completed = true,
            hosts = listOf(
                DpiProxyTestHostResult("youtube.com", 1, 1, 1, listOf(200)),
                DpiProxyTestHostResult("youtu.be", 1, 1, 1, listOf(200)),
            ),
        )
        val partialFast = DpiProxyTestStrategyResult(
            strategyB,
            backendStarted = true,
            completed = true,
            hosts = listOf(
                DpiProxyTestHostResult("youtube.com", 1, 1, 1, listOf(5)),
                DpiProxyTestHostResult("youtu.be", 1, 1, 0, emptyList()),
            ),
        )
        assertEquals(strategyA, DpiProxyTestRanker.rank(listOf(partialFast, full)).first().strategy)
    }

    @Test fun `result summary retains reusable strategy and aggregate result`() {
        val strategy = DpiProxyTestCatalog.strategies.first()
        val result = DpiProxyTestStrategyResult(
            strategy = strategy,
            backendStarted = true,
            completed = true,
            hosts = listOf(DpiProxyTestHostResult("youtube.com", 1, 1, 1, listOf(12))),
        ).toSummary()

        assertEquals(strategy, result.strategy)
        assertEquals(1, result.hostCount)
        assertEquals(1, result.fullyWorkingHosts)
        assertTrue(result.fullCoverage)
    }

    @Test fun `reference configuration ranges match proxy test`() {
        DpiProxyTestConfig(1, 1, 1)
        DpiProxyTestConfig(20, 50, 15)
        assertEquals(1, DpiProxyTestConfig().attemptsPerHost)
        assertEquals(20, DpiProxyTestConfig().concurrency)
        assertEquals(5, DpiProxyTestConfig().timeoutSeconds)
    }

    @Test fun `http policy rejects legal block nginx drop and server errors`() {
        assertTrue(DpiProxyHttpPolicy.isReachable(200))
        assertTrue(DpiProxyHttpPolicy.isReachable(404))
        assertFalse(DpiProxyHttpPolicy.isReachable(444))
        assertFalse(DpiProxyHttpPolicy.isReachable(451))
        assertFalse(DpiProxyHttpPolicy.isReachable(500))
    }

    @Test fun `probe deadline closes a blocked proxy socket`() = runBlocking {
        HangingSocksServer().use { server ->
            val observation = AuthenticatedSocksHttpsProbe(
                proxyPort = server.port,
                timeoutMs = 200,
                credentials = ProbeCredentials("probe-user", "probe-password"),
                cancelled = { false },
            ).probe("example.com")

            assertTrue(server.requestReceived.await(1, TimeUnit.SECONDS))
            assertFalse(observation.success)
            assertTrue(server.clientDisconnected.await(1, TimeUnit.SECONDS))
        }
    }

    @Test fun `probe cancellation closes a blocked proxy socket`() = runBlocking {
        val cancelled = AtomicBoolean(false)
        HangingSocksServer().use { server ->
            val probe = AuthenticatedSocksHttpsProbe(
                proxyPort = server.port,
                timeoutMs = 5_000,
                credentials = ProbeCredentials("probe-user", "probe-password"),
                cancelled = cancelled::get,
            )
            val result = async(Dispatchers.IO) { probe.probe("example.com") }
            assertTrue(server.requestReceived.await(2, TimeUnit.SECONDS))

            cancelled.set(true)
            var cancellationObserved = false
            try {
                result.await()
            } catch (_: CancellationException) {
                cancellationObserved = true
            }

            assertTrue(cancellationObserved)
            assertTrue(server.clientDisconnected.await(1, TimeUnit.SECONDS))
        }
    }

    private class HangingSocksServer : AutoCloseable {
        private val server = ServerSocket(0)
        val requestReceived = CountDownLatch(1)
        val clientDisconnected = CountDownLatch(1)
        val port: Int get() = server.localPort

        private val worker = thread(start = true, isDaemon = true, name = "dpi-proxy-test-server") {
            try {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val greeting = ByteArray(3)
                    var offset = 0
                    while (offset < greeting.size) {
                        val count = input.read(greeting, offset, greeting.size - offset)
                        if (count < 0) return@use
                        offset += count
                    }
                    requestReceived.countDown()
                    while (input.read() >= 0) {
                        // Intentionally do not send a SOCKS method response.
                    }
                }
            } catch (_: IOException) {
                // Expected when the test closes either side of the socket.
            } finally {
                clientDisconnected.countDown()
            }
        }

        override fun close() {
            runCatching { server.close() }
            worker.join(1_000)
        }
    }
}
