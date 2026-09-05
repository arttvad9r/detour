package dev.triplet.app.ui

import dev.triplet.app.core.DpiAutoDomainPlan
import dev.triplet.app.core.DpiAutoSearchReport
import dev.triplet.app.core.DpiPerDomainPlan
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.DpiProbeTarget
import dev.triplet.app.core.DpiScopeStrategyAssignment
import dev.triplet.app.core.DpiStrategyCatalog
import dev.triplet.app.core.DpiStrategyResult
import dev.triplet.app.core.DpiTargetResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoUiStateTest {
    private val target = DpiProbeTarget("blocked", "blocked.example")
    private val candidate = requireNotNull(DpiStrategyCatalog.byId("split-sni"))
    private val winner = DpiStrategyResult(
        candidate = candidate,
        backendStarted = true,
        targets = listOf(DpiTargetResult(target, attempts = 2, successes = 2)),
    )
    private val report = DpiAutoSearchReport(
        baseline = listOf(DpiTargetResult(target, attempts = 2, successes = 0)),
        strategies = listOf(winner),
    )
    private val completePlan = DpiPerDomainPlan(
        directTargets = emptyList(),
        assignments = listOf(
            DpiScopeStrategyAssignment(
                scopeHost = target.scopeHost,
                targets = listOf(target),
                candidate = candidate,
            ),
        ),
        unresolvedScopeHosts = emptyList(),
    )

    @Test fun `automatic test requires idle vpn valid targets attempts and released test proxy`() {
        assertTrue(DpiUiState(vpnIdle = true, selectedAutoGroups = setOf("youtube")).canRunAuto)
        assertTrue(
            DpiUiState(
                vpnIdle = true,
                selectedAutoGroups = emptySet(),
                customAutoDomains = "example.com",
            ).canRunAuto,
        )
        assertFalse(DpiUiState(vpnIdle = false, selectedAutoGroups = setOf("youtube")).canRunAuto)
        assertFalse(DpiUiState(vpnIdle = true, selectedAutoGroups = emptySet()).canRunAuto)
        assertFalse(
            DpiUiState(
                vpnIdle = true,
                selectedAutoGroups = setOf("youtube"),
                autoAttempts = 0,
            ).canRunAuto,
        )
        assertFalse(
            DpiUiState(
                vpnIdle = true,
                selectedAutoGroups = setOf("youtube"),
                autoAttempts = 21,
            ).canRunAuto,
        )
        assertFalse(
            DpiUiState(
                vpnIdle = true,
                selectedAutoGroups = emptySet(),
                customAutoDomains = "https://example.com",
                customAutoDomainsInvalid = true,
            ).canRunAuto,
        )
        assertFalse(
            DpiUiState(
                vpnIdle = true,
                selectedAutoGroups = setOf("youtube"),
                autoRunState = DpiAutoRunState.CANCELLING,
            ).canRunAuto,
        )
    }

    @Test fun `complete per-domain plan can be applied only after test completes`() {
        assertTrue(
            DpiUiState(
                autoRunState = DpiAutoRunState.COMPLETE,
                autoReport = report,
                autoDomainPlan = completePlan,
            ).canApplyAuto,
        )
        assertFalse(
            DpiUiState(
                autoRunState = DpiAutoRunState.RUNNING,
                autoReport = report,
                autoDomainPlan = completePlan,
            ).canApplyAuto,
        )
        assertFalse(
            DpiUiState(
                autoRunState = DpiAutoRunState.COMPLETE,
                autoReport = report,
            ).canApplyAuto,
        )
    }

    @Test fun `all direct or unresolved result cannot be applied`() {
        val allDirect = DpiPerDomainPlan(
            directTargets = listOf(target),
            assignments = emptyList(),
            unresolvedScopeHosts = emptyList(),
        )
        val unresolved = DpiPerDomainPlan(
            directTargets = emptyList(),
            assignments = emptyList(),
            unresolvedScopeHosts = listOf(target.scopeHost),
        )

        assertFalse(
            DpiUiState(
                autoRunState = DpiAutoRunState.COMPLETE,
                autoReport = report,
                autoDomainPlan = allDirect,
            ).canApplyAuto,
        )
        assertFalse(
            DpiUiState(
                autoRunState = DpiAutoRunState.COMPLETE,
                autoReport = report,
                autoDomainPlan = unresolved,
            ).canApplyAuto,
        )
    }

    @Test fun `already active automatic domain plan does not offer duplicate apply`() {
        val persisted = DpiAutoDomainPlan.fromPlan(completePlan)
        val state = DpiUiState(
            preset = DpiPreset.AUTO,
            autoRunState = DpiAutoRunState.COMPLETE,
            autoReport = report,
            autoDomainPlan = completePlan,
            appliedAutoDomainPlan = persisted,
        )

        assertTrue(state.autoPlanApplied)
        assertFalse(state.canApplyAuto)
    }

    @Test fun `saved group decoder filters unknown ids`() {
        val groups = DpiViewModel.decodeGroupIds("youtube,unknown,discord")
        assertTrue("youtube" in groups)
        assertTrue("discord" in groups)
        assertFalse("unknown" in groups)
    }
}
