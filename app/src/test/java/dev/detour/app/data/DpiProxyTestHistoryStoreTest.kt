package dev.detour.app.data

import dev.detour.app.core.DpiProxyTestCatalog
import dev.detour.app.core.DpiProxyTestConfig
import dev.detour.app.core.DpiProxyTestResultSummary
import dev.detour.app.core.DpiProxyTestRun
import dev.detour.app.core.DpiProxyTestStrategySelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiProxyTestHistoryStoreTest {
    @Test fun `history codec round trips reference and custom results`() {
        val reference = DpiProxyTestCatalog.strategies.first()
        val custom = requireNotNull(DpiProxyTestStrategySelection.custom("-d1 -s1+s -r1+s -a1"))
        val run = DpiProxyTestRun(
            id = "run-1",
            createdAtEpochMs = 1_700_000_000_000,
            selectedDomainIds = setOf("youtube", "googlevideo"),
            config = DpiProxyTestConfig(attemptsPerHost = 2, concurrency = 8, timeoutSeconds = 4),
            results = listOf(
                summary(reference, hostCount = 32, workingHosts = 32, successes = 64, attempts = 64, median = 20),
                summary(custom, hostCount = 32, workingHosts = 30, successes = 61, attempts = 64, median = 18),
            ),
        )

        val decoded = DpiProxyTestHistoryCodec.decode(DpiProxyTestHistoryCodec.encode(listOf(run)))

        assertEquals(listOf(run), decoded)
        assertEquals(DpiProxyTestStrategySelection.CUSTOM_STRATEGY_ID, decoded.single().results[1].strategy.id)
    }

    @Test fun `unknown history version fails closed`() {
        assertTrue(DpiProxyTestHistoryCodec.decode("{\"version\":99,\"runs\":[]}").isEmpty())
    }

    private fun summary(
        strategy: dev.detour.app.core.DpiProxyTestStrategy,
        hostCount: Int,
        workingHosts: Int,
        successes: Int,
        attempts: Int,
        median: Long,
    ) = DpiProxyTestResultSummary(
        strategy = strategy,
        backendStarted = true,
        completed = true,
        hostCount = hostCount,
        fullyWorkingHosts = workingHosts,
        totalSuccesses = successes,
        totalAttempts = attempts,
        medianLatencyMs = median,
    )
}
