package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoStrategyTest {
    private val youtube = DpiProbeTarget("youtube", "www.youtube.com")
    private val discord = DpiProbeTarget("discord", "discord.com")

    @Test fun `catalog uses only currently validated pinned arguments`() {
        assertTrue(DpiStrategyCatalog.default.isNotEmpty())
        assertEquals(
            DpiStrategyCatalog.default.size,
            DpiStrategyCatalog.default.map { it.id }.distinct().size,
        )
        assertTrue(
            DpiStrategyCatalog.default.all { DpiArgs.isValid(it.args.joinToString(" ")) },
        )
    }

    @Test fun `ranker prefers complete domain coverage over lower latency`() {
        val complete = result(
            id = "complete",
            youtube = target(youtube, successes = 2, latencies = listOf(400, 450)),
            discord = target(discord, successes = 2, latencies = listOf(420, 460)),
        )
        val fastButPartial = result(
            id = "fast-partial",
            youtube = target(youtube, successes = 2, latencies = listOf(20, 25)),
            discord = target(discord, successes = 1, latencies = listOf(20)),
        )

        assertEquals(
            listOf("complete", "fast-partial"),
            DpiStrategyRanker.rank(listOf(fastButPartial, complete)).map { it.candidate.id },
        )
    }

    @Test fun `ranker uses latency only after success metrics tie`() {
        val slow = result(
            id = "slow",
            youtube = target(youtube, successes = 2, latencies = listOf(300, 350)),
            discord = target(discord, successes = 2, latencies = listOf(320, 370)),
        )
        val fast = result(
            id = "fast",
            youtube = target(youtube, successes = 2, latencies = listOf(80, 90)),
            discord = target(discord, successes = 2, latencies = listOf(85, 95)),
        )

        assertEquals(
            listOf("fast", "slow"),
            DpiStrategyRanker.rank(listOf(slow, fast)).map { it.candidate.id },
        )
    }

    @Test fun `runner isolates candidates and always stops backend`() {
        val starts = mutableListOf<String>()
        var stops = 0
        val backend = object : DpiStrategyBackend {
            override fun start(candidate: DpiStrategyCandidate): Boolean {
                starts += candidate.id
                return candidate.id != "broken"
            }

            override fun stop() {
                stops++
            }
        }
        val probe = DpiTargetProbe { DpiProbeAttempt(success = true, latencyMs = 10) }
        val candidates = listOf(
            DpiStrategyCandidate("broken", listOf("-d", "1")),
            DpiStrategyCandidate("working", listOf("-s", "1+s")),
        )

        val results = DpiStrategySearchRunner(backend, probe).run(
            candidates = candidates,
            targets = listOf(youtube, discord),
            attemptsPerTarget = 2,
        )

        assertEquals(listOf("broken", "working"), starts)
        assertEquals(2, stops)
        assertEquals("working", results.first().candidate.id)
        assertTrue(results.first().backendStarted)
        assertEquals(2, results.first().fullyWorkingTargets)
        assertFalse(results.last().backendStarted)
        assertEquals(0, results.last().totalAttempts)
    }

    @Test fun `runner records repeated per-domain observations`() {
        val backend = object : DpiStrategyBackend {
            override fun start(candidate: DpiStrategyCandidate) = true
            override fun stop() = Unit
        }
        var call = 0
        val probe = DpiTargetProbe {
            call++
            DpiProbeAttempt(success = call != 2, latencyMs = if (call != 2) call * 10L else null)
        }
        val candidate = DpiStrategyCandidate("candidate", listOf("-d", "1"))

        val result = DpiStrategySearchRunner(backend, probe).run(
            candidates = listOf(candidate),
            targets = listOf(youtube),
            attemptsPerTarget = 3,
        ).single()

        assertEquals(3, result.totalAttempts)
        assertEquals(2, result.totalSuccesses)
        assertEquals(0, result.fullyWorkingTargets)
        assertEquals(1, result.reachableTargets)
        assertEquals(listOf(10L, 30L), result.targets.single().successfulLatenciesMs)
    }

    private fun result(
        id: String,
        youtube: DpiTargetResult,
        discord: DpiTargetResult,
    ) = DpiStrategyResult(
        candidate = DpiStrategyCandidate(id, listOf("-d", "1")),
        backendStarted = true,
        targets = listOf(youtube, discord),
    )

    private fun target(
        target: DpiProbeTarget,
        successes: Int,
        latencies: List<Long>,
    ) = DpiTargetResult(
        target = target,
        attempts = 2,
        successes = successes,
        successfulLatenciesMs = latencies,
    )
}
