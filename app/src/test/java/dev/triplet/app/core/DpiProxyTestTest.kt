package dev.triplet.app.core

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
    }

    @Test fun `exact reference command is valid custom but edited reference is rejected`() {
        val strategy = DpiProxyTestCatalog.strategies.first()
        assertTrue(DpiArgs.isValid(strategy.command))
        assertEquals(strategy.args, DpiArgs.resolve(DpiPreset.CUSTOM, strategy.command))
        assertFalse(DpiArgs.isValid(strategy.command + " -p 9999"))
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

    @Test fun `reference configuration ranges match proxy test`() {
        DpiProxyTestConfig(1, 1, 1)
        DpiProxyTestConfig(20, 50, 15)
        assertEquals(1, DpiProxyTestConfig().attemptsPerHost)
        assertEquals(20, DpiProxyTestConfig().concurrency)
        assertEquals(5, DpiProxyTestConfig().timeoutSeconds)
    }

    @Test fun `http policy rejects legal block and server errors`() {
        assertTrue(DpiProxyHttpPolicy.isReachable(200))
        assertTrue(DpiProxyHttpPolicy.isReachable(404))
        assertFalse(DpiProxyHttpPolicy.isReachable(451))
        assertFalse(DpiProxyHttpPolicy.isReachable(500))
    }
}
