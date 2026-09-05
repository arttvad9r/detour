package dev.triplet.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoDomainPlanTest {
    @Test fun `stored plan round trips normalized trusted scope mapping`() {
        val plan = DpiAutoDomainPlan.of(
            linkedMapOf(
                "YouTube.COM." to "split-sni",
                "discord.com" to "disorder-1",
            ),
        )

        val restored = DpiAutoDomainPlan.fromStored(plan.toStored())

        assertEquals(plan, restored)
        assertEquals(
            mapOf("discord.com" to "disorder-1", "youtube.com" to "split-sni"),
            restored?.scopeCandidates,
        )
    }

    @Test fun `stored plan rejects unknown candidate malformed scope and version`() {
        assertNull(
            DpiAutoDomainPlan.fromStored(
                """{"v":1,"scopes":{"example.com":"unknown"}}""",
            ),
        )
        assertNull(
            DpiAutoDomainPlan.fromStored(
                """{"v":1,"scopes":{"https://example.com":"split-sni"}}""",
            ),
        )
        assertNull(
            DpiAutoDomainPlan.fromStored(
                """{"v":99,"scopes":{"example.com":"split-sni"}}""",
            ),
        )
    }

    @Test fun `persisted plan recompiles from current trusted catalog`() {
        val plan = DpiAutoDomainPlan.of(
            mapOf(
                "youtube.com" to "split-sni",
                "discord.com" to "disorder-1",
            ),
        )

        val args = plan.compileArgs()

        assertTrue(args.containsAll(listOf("-H", "-A", "n", "youtube.com".let { ":$it" })))
        assertEquals(1, args.windowed(2).count { it == listOf("--timeout", "3") })
    }

    @Test fun `persisted plan accepts sixty four scopes when they compile to one group`() {
        val scopes = (1..DpiDomainInput.MAX_DOMAINS).associate { index ->
            "d$index.example.com" to "split-sni"
        }
        val plan = DpiAutoDomainPlan.of(scopes)

        val args = plan.compileArgs()
        val restored = DpiAutoDomainPlan.fromStored(plan.toStored())

        assertEquals(1, args.count { it == "-H" })
        assertEquals(plan, restored)
    }

    @Test fun `persisted plan rejects more scopes than domain input supports`() {
        val scopes = (1..DpiDomainInput.MAX_DOMAINS + 1).associate { index ->
            "d$index.example.com" to "split-sni"
        }

        assertThrows(IllegalArgumentException::class.java) {
            DpiAutoDomainPlan.of(scopes)
        }
    }

    @Test fun `plan construction rejects more than thirty compiled groups`() {
        val first = requireNotNull(DpiStrategyCatalog.byId("split-sni"))
        val second = requireNotNull(DpiStrategyCatalog.byId("disorder-1"))
        var scope = "example.com"
        val assignments = (1..31).map { index ->
            scope = "s$index.$scope"
            val target = DpiProbeTarget("nested-$index", scope, scope)
            DpiScopeStrategyAssignment(
                scopeHost = scope,
                targets = listOf(target),
                candidate = if (index % 2 == 0) first else second,
            )
        }
        val plan = DpiPerDomainPlan(
            directTargets = emptyList(),
            assignments = assignments,
            unresolvedScopeHosts = emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            DpiAutoDomainPlan.fromPlan(plan)
        }
    }

    @Test fun `plan from search result stores scope rather than concrete probe endpoint`() {
        val redirector = DpiProbeTarget(
            "redirector",
            "redirector.googlevideo.com",
            "googlevideo.com",
        )
        val candidate = requireNotNull(DpiStrategyCatalog.byId("split-sni"))
        val searchPlan = DpiPerDomainPlan(
            directTargets = emptyList(),
            assignments = listOf(
                DpiScopeStrategyAssignment("googlevideo.com", listOf(redirector), candidate),
            ),
            unresolvedScopeHosts = emptyList(),
        )

        val persisted = DpiAutoDomainPlan.fromPlan(searchPlan)

        assertEquals(mapOf("googlevideo.com" to "split-sni"), persisted.scopeCandidates)
        assertTrue(persisted.compileArgs().contains(":googlevideo.com"))
    }

    @Test fun `empty persisted plan cannot be created`() {
        assertThrows(IllegalArgumentException::class.java) {
            DpiAutoDomainPlan.of(emptyMap())
        }
    }
}
