package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoProgressTest {
    @Test fun `progress fraction follows completed steps`() {
        val progress = DpiAutoProgress(
            phase = DpiAutoProgressPhase.STRATEGY,
            completed = 3,
            total = 8,
            currentId = "split-sni",
        )

        assertEquals(3f / 8f, progress.fraction)
        assertTrue(runCatching { progress.copy(completed = 9) }.isFailure)
        assertTrue(runCatching { progress.copy(total = 0) }.isFailure)
        assertTrue(runCatching { progress.copy(currentId = "") }.isFailure)
    }

    @Test fun `baseline reports current target and completed target count`() {
        val first = DpiProbeTarget("first", "first.example")
        val second = DpiProbeTarget("second", "second.example")
        val events = mutableListOf<DpiAutoProgress>()
        val coordinator = DpiAutoSearchCoordinator(
            directProbe = DpiTargetProbe { DpiProbeAttempt(success = true, latencyMs = 1) },
            strategySearcher = DpiStrategySearcher { _, _ -> emptyList() },
        )

        coordinator.run(
            targets = listOf(first, second),
            attemptsPerTarget = 1,
            onProgress = events::add,
        )

        assertEquals(
            listOf(
                DpiAutoProgress(DpiAutoProgressPhase.BASELINE, 0, 2, "first"),
                DpiAutoProgress(DpiAutoProgressPhase.BASELINE, 1, 2, "first"),
                DpiAutoProgress(DpiAutoProgressPhase.BASELINE, 1, 2, "second"),
                DpiAutoProgress(DpiAutoProgressPhase.BASELINE, 2, 2, "second"),
            ),
            events,
        )
    }

    @Test fun `baseline cancellation does not report skipped work as complete`() {
        val first = DpiProbeTarget("first", "first.example")
        val second = DpiProbeTarget("second", "second.example")
        val events = mutableListOf<DpiAutoProgress>()
        var calls = 0
        val coordinator = DpiAutoSearchCoordinator(
            directProbe = DpiTargetProbe {
                calls++
                DpiProbeAttempt(success = false)
            },
            strategySearcher = DpiStrategySearcher { _, _ -> error("must not search") },
        )

        coordinator.run(
            targets = listOf(first, second),
            attemptsPerTarget = 2,
            cancelled = { calls >= 1 },
            onProgress = events::add,
        )

        assertEquals(
            listOf(DpiAutoProgress(DpiAutoProgressPhase.BASELINE, 0, 2, "first")),
            events,
        )
    }
}
