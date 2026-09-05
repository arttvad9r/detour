package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DpiPerDomainPlanTest {
    private val youtube = DpiProbeTarget("youtube", "youtube.com")
    private val youtubeWeb = DpiProbeTarget("youtube-web", "www.youtube.com")
    private val discord = DpiProbeTarget("discord", "discord.com")

    private val split = DpiStrategyCandidate(
        "split",
        listOf("-s", "1+s", "--timeout", "3"),
    )
    private val disorder = DpiStrategyCandidate(
        "disorder",
        listOf("-d", "1", "--timeout", "3"),
    )

    @Test fun `planner keeps direct targets out of DPI assignments`() {
        val report = DpiAutoSearchReport(
            baseline = listOf(
                target(youtube, successes = 0),
                target(discord, successes = 2),
            ),
            strategies = listOf(
                strategy(split, target(youtube, successes = 2, latencies = listOf(80, 90))),
            ),
        )

        val plan = DpiPerDomainPlanner.fromReport(report)

        assertEquals(listOf(discord), plan.directTargets)
        assertEquals(listOf(youtube), plan.assignments.map { it.target })
        assertEquals("split", plan.assignments.single().candidate.id)
        assertTrue(plan.complete)
    }

    @Test fun `planner chooses lower latency after equally stable success`() {
        val report = DpiAutoSearchReport(
            baseline = listOf(target(youtube, successes = 0)),
            strategies = listOf(
                strategy(split, target(youtube, successes = 2, latencies = listOf(200, 220))),
                strategy(disorder, target(youtube, successes = 2, latencies = listOf(40, 50))),
            ),
        )

        assertEquals(
            "disorder",
            DpiPerDomainPlanner.fromReport(report).assignments.single().candidate.id,
        )
    }

    @Test fun `planner reports target unresolved when no candidate fully works`() {
        val report = DpiAutoSearchReport(
            baseline = listOf(target(youtube, successes = 0)),
            strategies = listOf(
                strategy(split, target(youtube, successes = 1)),
                DpiStrategyResult(disorder, backendStarted = false, targets = listOf(target(youtube, 0, 0))),
            ),
        )

        val plan = DpiPerDomainPlanner.fromReport(report)

        assertFalse(plan.complete)
        assertEquals(listOf(youtube), plan.unresolvedTargets)
        assertTrue(plan.assignments.isEmpty())
    }

    @Test fun `compiler groups hosts sharing a candidate and emits one global timeout`() {
        val plan = DpiPerDomainPlan(
            directTargets = emptyList(),
            assignments = listOf(
                DpiTargetStrategyAssignment(youtube, split),
                DpiTargetStrategyAssignment(discord, split),
            ),
            unresolvedTargets = emptyList(),
        )

        val compiled = DpiPerDomainCommandCompiler.compile(plan)

        assertEquals(1, compiled.groups.size)
        assertEquals(listOf("discord.com", "youtube.com"), compiled.groups.single().hosts)
        assertEquals(
            listOf("--timeout", "3", "-H", ":discord.com youtube.com", "-s", "1+s"),
            compiled.args,
        )
    }

    @Test fun `compiler separates strategies with auto-none and leaves fallback implicit`() {
        val plan = DpiPerDomainPlan(
            directTargets = emptyList(),
            assignments = listOf(
                DpiTargetStrategyAssignment(youtube, split),
                DpiTargetStrategyAssignment(discord, disorder),
            ),
            unresolvedTargets = emptyList(),
        )

        val compiled = DpiPerDomainCommandCompiler.compile(plan)

        assertEquals(2, compiled.groups.size)
        assertEquals(1, compiled.args.windowed(2).count { it == listOf("-A", "n") })
        assertFalse(compiled.args.takeLast(2) == listOf("-A", "n"))
    }

    @Test fun `more specific host strategy precedes parent host strategy`() {
        val plan = DpiPerDomainPlan(
            directTargets = emptyList(),
            assignments = listOf(
                DpiTargetStrategyAssignment(youtube, split),
                DpiTargetStrategyAssignment(youtubeWeb, disorder),
            ),
            unresolvedTargets = emptyList(),
        )

        val compiled = DpiPerDomainCommandCompiler.compile(plan)

        assertEquals("disorder", compiled.groups[0].candidate.id)
        assertEquals(listOf("www.youtube.com"), compiled.groups[0].hosts)
        assertEquals("split", compiled.groups[1].candidate.id)
        assertEquals(listOf("youtube.com"), compiled.groups[1].hosts)
    }

    @Test fun `candidate grouping cycle falls back to correctly ordered singleton host groups`() {
        val a = DpiStrategyCandidate("a", listOf("-s", "1+s", "--timeout", "3"))
        val b = DpiStrategyCandidate("b", listOf("-d", "1", "--timeout", "3"))
        val assignments = listOf(
            DpiTargetStrategyAssignment(DpiProbeTarget("a-child", "a.example.com"), a),
            DpiTargetStrategyAssignment(DpiProbeTarget("a-parent", "example.org"), a),
            DpiTargetStrategyAssignment(DpiProbeTarget("b-parent", "example.com"), b),
            DpiTargetStrategyAssignment(DpiProbeTarget("b-child", "b.example.org"), b),
        )

        val compiled = DpiPerDomainCommandCompiler.compile(
            DpiPerDomainPlan(emptyList(), assignments, emptyList()),
        )

        assertEquals(4, compiled.groups.size)
        assertTrue(
            compiled.groups.indexOfFirst { "a.example.com" in it.hosts } <
                compiled.groups.indexOfFirst { "example.com" in it.hosts },
        )
        assertTrue(
            compiled.groups.indexOfFirst { "b.example.org" in it.hosts } <
                compiled.groups.indexOfFirst { "example.org" in it.hosts },
        )
    }

    @Test fun `compiler rejects unresolved plan`() {
        assertThrows(IllegalArgumentException::class.java) {
            DpiPerDomainCommandCompiler.compile(
                DpiPerDomainPlan(emptyList(), emptyList(), listOf(youtube)),
            )
        }
    }

    @Test fun `compiler rejects nested candidate group controls`() {
        val nested = DpiStrategyCandidate(
            "nested",
            listOf("-s", "1+s", "-A", "n", "-d", "1", "--timeout", "3"),
        )
        val plan = DpiPerDomainPlan(
            emptyList(),
            listOf(DpiTargetStrategyAssignment(youtube, nested)),
            emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            DpiPerDomainCommandCompiler.compile(plan)
        }
    }

    @Test fun `compiler rejects incompatible global timeout values`() {
        val slow = DpiStrategyCandidate("slow", listOf("-s", "1+s", "--timeout", "4"))
        val plan = DpiPerDomainPlan(
            emptyList(),
            listOf(
                DpiTargetStrategyAssignment(youtube, split),
                DpiTargetStrategyAssignment(discord, slow),
            ),
            emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            DpiPerDomainCommandCompiler.compile(plan)
        }
    }

    @Test fun `compiler caps explicit groups below unsafe pinned shift range`() {
        val assignments = (0..DpiPerDomainCommandCompiler.MAX_EXPLICIT_GROUPS).map { index ->
            DpiTargetStrategyAssignment(
                DpiProbeTarget("target-$index", "h$index.example$index.com"),
                DpiStrategyCandidate("candidate-$index", listOf("-s", "1+s", "--timeout", "3")),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            DpiPerDomainCommandCompiler.compile(
                DpiPerDomainPlan(emptyList(), assignments, emptyList()),
            )
        }
    }

    private fun strategy(
        candidate: DpiStrategyCandidate,
        result: DpiTargetResult,
    ) = DpiStrategyResult(candidate, backendStarted = true, targets = listOf(result))

    private fun target(
        target: DpiProbeTarget,
        successes: Int,
        attempts: Int = 2,
        latencies: List<Long> = emptyList(),
    ) = DpiTargetResult(
        target = target,
        attempts = attempts,
        successes = successes,
        successfulLatenciesMs = latencies,
    )
}
