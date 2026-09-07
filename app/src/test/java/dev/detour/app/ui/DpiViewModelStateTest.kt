package dev.detour.app.ui

import dev.detour.app.core.DpiPreset
import dev.detour.app.core.DpiProxyTestCatalog
import dev.detour.app.core.DpiProxyTestConfig
import dev.detour.app.core.DpiProxyTestResultSummary
import dev.detour.app.core.DpiProxyTestRun
import dev.detour.app.core.VlessKeys
import dev.detour.app.core.VpnProfileKind
import dev.detour.app.data.TriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiViewModelStateTest {
    @Test fun `saving state disables duplicate custom save`() {
        val state = dpiUiState(
            settings = null,
            customDraft = "-d 1 -s 2",
            editingOverride = true,
            saveState = DpiSaveState.SAVING,
        )

        assertEquals(DpiSaveState.SAVING, state.saveState)
        assertFalse(state.canSaveCustom)
    }

    @Test fun `failed custom save remains retryable`() {
        val state = dpiUiState(
            settings = null,
            customDraft = "-d 1 -s 2",
            editingOverride = true,
            saveState = DpiSaveState.ERROR,
        )

        assertEquals(DpiSaveState.ERROR, state.saveState)
        assertTrue(state.canSaveCustom)
    }

    @Test fun `pending recommended selection overrides lagging custom persistence`() {
        val state = dpiUiState(
            settings = settings(DpiPreset.CUSTOM, "-d 1 -s 2"),
            customDraft = null,
            editingOverride = null,
            presetOverride = DpiPreset.RECOMMENDED,
        )

        assertEquals(DpiPreset.RECOMMENDED, state.preset)
        assertFalse(state.editingCustom)
    }

    @Test fun `pending custom selection overrides lagging recommended persistence`() {
        val state = dpiUiState(
            settings = settings(DpiPreset.RECOMMENDED, "-d 1 -s 2"),
            customDraft = "-d 3 -s 4",
            editingOverride = true,
            presetOverride = DpiPreset.CUSTOM,
        )

        assertEquals(DpiPreset.CUSTOM, state.preset)
        assertTrue(state.editingCustom)
        assertTrue(state.customChanged)
    }

    @Test fun `cleared draft follows later persisted custom args`() {
        val state = dpiUiState(
            settings = settings(DpiPreset.CUSTOM, "-d 5 -s 6"),
            customDraft = null,
            editingOverride = null,
        )

        assertEquals("-d 5 -s 6", state.customField)
        assertFalse(state.customChanged)
    }

    @Test fun `selected historic proxy run controls displayed results`() {
        val first = proxyRun("first", 1_000, strategyIndex = 1)
        val second = proxyRun("second", 2_000, strategyIndex = 2)
        val state = DpiProxyTestUiState(
            historyLoaded = true,
            history = listOf(second, first),
            selectedRunId = first.id,
        )

        assertEquals(first, state.selectedRun)
        assertEquals(first.results, state.results)
        assertTrue(state.completed)
    }

    @Test fun `new test configuration does not erase historic proxy results`() {
        val run = proxyRun("saved", 1_000, strategyIndex = 1)
        val state = DpiProxyTestUiState(
            historyLoaded = true,
            history = listOf(run),
            selectedRunId = run.id,
            attemptsPerHost = 3,
            concurrency = 7,
            timeoutSeconds = 9,
        )

        assertEquals(run.results, state.results)
        assertTrue(state.canStart)
    }

    @Test fun `invalid custom proxy strategy blocks start without hiding history`() {
        val run = proxyRun("saved", 1_000, strategyIndex = 1)
        val state = DpiProxyTestUiState(
            historyLoaded = true,
            history = listOf(run),
            selectedRunId = run.id,
            customStrategyDraft = "--port 9999",
        )

        assertTrue(state.customStrategyInvalid)
        assertFalse(state.canStart)
        assertEquals(run.results, state.results)
    }

    private fun proxyRun(id: String, createdAt: Long, strategyIndex: Int): DpiProxyTestRun {
        val strategy = DpiProxyTestCatalog.strategies[strategyIndex - 1]
        return DpiProxyTestRun(
            id = id,
            createdAtEpochMs = createdAt,
            selectedDomainIds = setOf("youtube"),
            config = DpiProxyTestConfig(),
            results = listOf(
                DpiProxyTestResultSummary(
                    strategy = strategy,
                    backendStarted = true,
                    completed = true,
                    hostCount = 13,
                    fullyWorkingHosts = 13,
                    totalSuccesses = 13,
                    totalAttempts = 13,
                    medianLatencyMs = 25,
                ),
            ),
        )
    }

    private fun settings(preset: DpiPreset, customArgs: String) = TriSettings(
        vlessKeys = VlessKeys(emptyList(), null),
        warpProfile = null,
        activeVpn = VpnProfileKind.VLESS,
        preset = preset,
        dpiCustomArgs = customArgs,
        autoConnect = false,
        themeId = "",
        dnsId = "google",
        dnsCustom = "",
        routes = emptyMap(),
        showSystemApps = false,
        sessionStartedAt = null,
    )
}
