package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoSearchTest {
    private val direct = DpiProbeTarget("direct", "direct.example")
    private val blocked = DpiProbeTarget("blocked", "blocked.example")

    @Test fun `strategy search only receives targets that fail direct baseline`() {
        val searched = mutableListOf<List<DpiProbeTarget>>()
        val coordinator = DpiAutoSearchCoordinator(
            directProbe = DpiTargetProbe { target ->
                DpiProbeAttempt(success = target == direct, latencyMs = 10)
            },
            strategySearcher = DpiStrategySearcher { targets, _ ->
                searched += targets
                listOf(successfulStrategy(targets))
            },
        )

        val report = coordinator.run(listOf(direct, blocked), attemptsPerTarget = 2)

        assertEquals(listOf(blocked), searched.single())
        assertEquals(listOf(blocked), report.problematicTargets)
        assertFalse(report.allDirect)
        assertEquals("winner", report.winner?.candidate?.id)
    }

    @Test fun `all direct targets skip strategy search`() {
        var searches = 0
        val coordinator = DpiAutoSearchCoordinator(
            directProbe = DpiTargetProbe { DpiProbeAttempt(success = true, latencyMs = 5) },
            strategySearcher = DpiStrategySearcher { _, _ ->
                searches++
                emptyList()
            },
        )

        val report = coordinator.run(listOf(direct, blocked), attemptsPerTarget = 2)

        assertEquals(0, searches)
        assertTrue(report.allDirect)
        assertTrue(report.strategies.isEmpty())
        assertNull(report.winner)
    }

    @Test fun `partial winner is not applicable`() {
        val candidate = DpiStrategyCandidate("partial", listOf("-d", "1"))
        val result = DpiStrategyResult(
            candidate = candidate,
            backendStarted = true,
            targets = listOf(
                DpiTargetResult(blocked, attempts = 2, successes = 1, successfulLatenciesMs = listOf(12)),
            ),
        )
        val coordinator = DpiAutoSearchCoordinator(
            directProbe = DpiTargetProbe { DpiProbeAttempt(false) },
            strategySearcher = DpiStrategySearcher { _, _ -> listOf(result) },
        )

        assertNull(coordinator.run(listOf(blocked), attemptsPerTarget = 2).winner)
    }

    @Test fun `baseline cancellation preserves stable result shape and skips strategies`() {
        var calls = 0
        var searches = 0
        val coordinator = DpiAutoSearchCoordinator(
            directProbe = DpiTargetProbe {
                calls++
                DpiProbeAttempt(false)
            },
            strategySearcher = DpiStrategySearcher { _, _ ->
                searches++
                emptyList()
            },
        )

        val report = coordinator.run(
            targets = listOf(direct, blocked),
            attemptsPerTarget = 2,
            cancelled = { calls >= 1 },
        )

        assertEquals(2, report.baseline.size)
        assertEquals(0, searches)
        assertEquals(1, report.baseline.first().attempts)
        assertEquals(0, report.baseline.last().attempts)
    }

    private fun successfulStrategy(targets: List<DpiProbeTarget>): DpiStrategyResult =
        DpiStrategyResult(
            candidate = DpiStrategyCandidate("winner", listOf("-d", "1")),
            backendStarted = true,
            targets = targets.map {
                DpiTargetResult(it, attempts = 2, successes = 2, successfulLatenciesMs = listOf(20, 21))
            },
        )
}
