package dev.triplet.app.ui

import dev.triplet.app.core.DpiAutoSearchReport
import dev.triplet.app.core.DpiPreset
import dev.triplet.app.core.DpiProbeTarget
import dev.triplet.app.core.DpiStrategyCandidate
import dev.triplet.app.core.DpiStrategyResult
import dev.triplet.app.core.DpiTargetResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiAutoUiStateTest {
    private val target = DpiProbeTarget("blocked", "blocked.example")
    private val winner = DpiStrategyResult(
        candidate = DpiStrategyCandidate("winner", listOf("-d", "1")),
        backendStarted = true,
        targets = listOf(DpiTargetResult(target, attempts = 2, successes = 2)),
    )
    private val report = DpiAutoSearchReport(
        baseline = listOf(DpiTargetResult(target, attempts = 2, successes = 0)),
        strategies = listOf(winner),
    )

    @Test fun `automatic test requires idle vpn valid targets and released test proxy`() {
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

    @Test fun `automatic result can only be applied after complete full winner`() {
        assertTrue(
            DpiUiState(
                autoRunState = DpiAutoRunState.COMPLETE,
                autoReport = report,
            ).canApplyAuto,
        )
        assertFalse(
            DpiUiState(
                autoRunState = DpiAutoRunState.RUNNING,
                autoReport = report,
            ).canApplyAuto,
        )
    }

    @Test fun `already active automatic winner does not offer duplicate apply`() {
        assertFalse(
            DpiUiState(
                preset = DpiPreset.AUTO,
                autoRunState = DpiAutoRunState.COMPLETE,
                autoReport = report,
                appliedAutoCandidateId = "winner",
            ).canApplyAuto,
        )
    }

    @Test fun `saved group decoder filters unknown ids`() {
        val groups = DpiViewModel.decodeGroupIds("youtube,unknown,discord")
        assertTrue("youtube" in groups)
        assertTrue("discord" in groups)
        assertFalse("unknown" in groups)
    }
}
