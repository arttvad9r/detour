package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiPerDomainPlanTest {
    private val youtube = DpiProbeTarget("youtube", "youtube.com", "youtube.com")
    private val youtubeWeb = DpiProbeTarget("youtube-web", "www.youtube.com", "youtube.com")
    private val discord = DpiProbeTarget("discord", "discord.com", "discord.com")

    private val split = DpiStrategyCandidate(
        "split",
        listOf("-s", "1+s", "--timeout", "3"),
    )
    private val disorder = DpiStrategyCandidate(
        "disorder",
        listOf("-d", "1", "--timeout", "3"),
    )

    @Test fun `planner keeps unaffected direct scopes out of DPI assignments`() {
        val report = DpiAutoSearchReport(
            baseline = listOf(
                target(youtube, successes = 0),
                target(youtubeWeb, successes = 2),
                target(discord, successes = 2),
            ),
            strategies = listOf(
                strategy(
                    split,
                    target(youtube, successes = 2, latencies = listOf(80, 90)),
                    target(youtubeWeb, successes = 2, latencies = listOf(70, 75)),
                ),
            ),
        )

        val plan = DpiPerDomainPlanner.fromReport(report)

        assertEquals(listOf(discord), plan.directTargets)
        assertEquals(listOf("youtube.com"), plan.assignments.map { it.scopeHost })
        assertEquals(listOf(youtube, youtubeWeb), plan.assignments.single().targets)
        assertEquals("split", plan.assignments.single().candidate.id)
        assertTrue(plan.complete)
    }

    @Test fun `planner refuses broad scope strategy that regresses direct-working peer`() {
        val report = DpiAutoSearchReport(
            baseline = listOf(
                target(youtube, successes = 0),
                target(youtubeWeb, successes = 2),
            ),
            strategies = listOf(
                strategy(
                    split,
                    target(youtube, successes = 2),
                    target(youtubeWeb, successes = 1),
                ),
            ),
        )

        val plan = DpiPerDomainPlanner.fromReport(report)

        assertFalse(plan.complete)
        assertEquals(listOf("youtube.com"), plan.unresolvedScopeHosts)
        assertTrue(plan.assignments.isEmpty())
    }

    @Test fun `planner chooses lower aggregate latency after equal scope stability`() {
        val report = DpiAutoSearchReport(
            baseline = listOf(
                target(youtube, successes = 0),
                target(youtubeWeb, successes = 2),
            ),
            strategies = listOf(
                strategy(
                    split,
                    target(youtube, successes = 2, latencies = listOf(200, 220)),
                    target(youtubeWeb, successes = 2, latencies = listOf(180, 210)),
                ),
                strategy(
                    disorder,
                    target(youtube, successes = 2, latencies = listOf(40, 50)),
                    target(youtubeWeb, successes = 2, latencies = listOf(45, 55)),
                ),
            ),
        )

        assertEquals(
            "disorder",
            DpiPerDomainPlanner.fromReport(report).assignments.single().candidate.id,
        )
    }

    @Test fun `compiler groups scopes sharing a candidate and emits one global timeout`() {
        val plan = DpiPerDomainPlan(
            directTargets = emptyList(),
            assignments = listOf(
                assignment("youtube.com", split, youtube, youtubeWeb),
                assignment("discord.com", split, discord),
            ),
            unresolvedScopeHosts = emptyList(),
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
                assignment("youtube.com", split, youtube, youtubeWeb),
                assignment("discord.com", disorder, discord),
            ),
            unresolvedScopeHosts = emptyList(),
        )

        val compiled = DpiPerDomainCommandCompiler.compile(plan)

        assertEquals(2, compiled.groups.size)
        assertEquals(1, compiled.args.windowed(2).count { it == listOf("-A", "n") })
        assertFalse(compiled.args.takeLast(2) == listOf("-A", "n"))
    }

    @Test fun `more specific rule scope precedes parent scope`() {
        val child = DpiProbeTarget("child", "www.youtube.com", "www.youtube.com")
        val parent = DpiProbeTarget("parent", "youtube.com", "youtube.com")
        val plan = DpiPerDomainPlan(
            directTargets = emptyList(),
            assignments = listOf(
                assignment("youtube.com", split, parent),
                assignment("www.youtube.com", disorder, child),
            ),
            unresolvedScopeHosts = emptyList(),
        )

        val compiled = DpiPerDomainCommandCompiler.compile(plan)

        assertEquals("disorder", compiled.groups[0].candidate.id)
        assertEquals(listOf("www.youtube.com"), compiled.groups[0].hosts)
        assertEquals("split", compiled.groups[1].candidate.id)
        assertEquals(listOf("youtube.com"), compiled.groups[1].hosts)
    }

    @Test fun `candidate grouping cycle falls back to correctly ordered singleton scopes`() {
        val a = DpiStrategyCandidate("a", listOf("-s", "1+s", "--timeout", "3"))
        val b = DpiStrategyCandidate("b", listOf("-d", "1", "--timeout", "3"))
        val assignments = listOf(
            scoped("a-child", "a.example.com", a),
            scoped("a-parent", "example.org", a),
            scoped("b-parent", "example.com", b),
            scoped("b-child", "b.example.org", b),
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
                DpiPerDomainPlan(emptyList(), emptyList(), listOf("youtube.com")),
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
            listOf(assignment("youtube.com", nested, youtube, youtubeWeb)),
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
                assignment("youtube.com", split, youtube, youtubeWeb),
                assignment("discord.com", slow, discord),
            ),
            emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            DpiPerDomainCommandCompiler.compile(plan)
        }
    }

    @Test fun `compiler caps explicit groups below unsafe pinned shift range`() {
        val assignments = (0..DpiPerDomainCommandCompiler.MAX_EXPLICIT_GROUPS).map { index ->
            val scope = "h$index.example$index.com"
            scoped(
                id = "target-$index",
                scope = scope,
                candidate = DpiStrategyCandidate(
                    "candidate-$index",
                    listOf("-s", "1+s", "--timeout", "3"),
                ),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            DpiPerDomainCommandCompiler.compile(
                DpiPerDomainPlan(emptyList(), assignments, emptyList()),
            )
        }
    }

    private fun assignment(
        scope: String,
        candidate: DpiStrategyCandidate,
        vararg targets: DpiProbeTarget,
    ) = DpiScopeStrategyAssignment(scope, targets.toList(), candidate)

    private fun scoped(
        id: String,
        scope: String,
        candidate: DpiStrategyCandidate,
    ): DpiScopeStrategyAssignment {
        val target = DpiProbeTarget(id, scope, scope)
        return assignment(scope, candidate, target)
    }

    private fun strategy(
        candidate: DpiStrategyCandidate,
        vararg results: DpiTargetResult,
    ) = DpiStrategyResult(candidate, backendStarted = true, targets = results.toList())

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
