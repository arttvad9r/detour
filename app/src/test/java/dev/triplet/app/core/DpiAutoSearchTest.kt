package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoSearchTest {
    private val direct = DpiProbeTarget("direct", "direct.example")
    private val blocked = DpiProbeTarget("blocked", "blocked.example")

    @Test fun `global strategy tests failures first then regression checks direct targets`() {
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

        assertEquals(listOf(blocked, direct), searched.single())
        assertEquals(listOf(blocked), report.problematicTargets)
        assertFalse(report.allDirect)
        assertEquals("winner", report.winner?.candidate?.id)
    }

    @Test fun `global candidate that fixes block but breaks direct peer is not applicable`() {
        val coordinator = DpiAutoSearchCoordinator(
            directProbe = DpiTargetProbe { target ->
                DpiProbeAttempt(success = target == direct, latencyMs = 10)
            },
            strategySearcher = DpiStrategySearcher { targets, _ ->
                val candidate = DpiStrategyCandidate("regression", listOf("-d", "1"))
                listOf(
                    DpiStrategyResult(
                        candidate = candidate,
                        backendStarted = true,
                        targets = targets.map { target ->
                            if (target == blocked) {
                                DpiTargetResult(target, 2, 2, listOf(20, 21))
                            } else {
                                DpiTargetResult(target, 1, 0)
                            }
                        },
                    ),
                )
            },
        )

        assertNull(coordinator.run(listOf(direct, blocked), attemptsPerTarget = 2).winner)
    }

    @Test fun `per-domain search retests direct peer inside affected broad scope`() {
        val root = DpiProbeTarget("root", "example.com", "example.com")
        val web = DpiProbeTarget("web", "www.example.com", "example.com")
        val unrelated = DpiProbeTarget("other", "other.example", "other.example")
        val searched = mutableListOf<List<DpiProbeTarget>>()
        val coordinator = DpiPerDomainSearchCoordinator(
            directProbe = DpiTargetProbe { target ->
                DpiProbeAttempt(success = target != root, latencyMs = 10)
            },
            strategySearcher = DpiStrategySearcher { targets, _ ->
                searched += targets
                listOf(successfulStrategy(targets))
            },
        )

        val report = coordinator.run(listOf(root, web, unrelated), attemptsPerTarget = 2)

        assertEquals(listOf(root, web), searched.single())
        assertEquals(listOf(root), report.problematicTargets)
        assertEquals(2, report.strategies.single().targets.size)
    }

    @Test fun `per-domain search skips unaffected scopes`() {
        val affected = DpiProbeTarget("affected", "a.example", "example")
        val peer = DpiProbeTarget("peer", "b.example", "example")
        val unrelated = DpiProbeTarget("unrelated", "unrelated.test", "unrelated.test")
        val searched = mutableListOf<List<DpiProbeTarget>>()
        val coordinator = DpiPerDomainSearchCoordinator(
            directProbe = DpiTargetProbe { target ->
                DpiProbeAttempt(success = target != affected, latencyMs = 10)
            },
            strategySearcher = DpiStrategySearcher { targets, _ ->
                searched += targets
                emptyList()
            },
        )

        coordinator.run(listOf(affected, peer, unrelated), attemptsPerTarget = 1)

        assertEquals(listOf(affected, peer), searched.single())
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
