package dev.triplet.app.ui

import androidx.lifecycle.SavedStateHandle
import dev.triplet.app.data.AppInfo
import dev.triplet.app.data.TriSettings
import dev.triplet.app.vpn.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedStateViewModelTest {
    @Test fun appsQueryRestoresFromSavedState() {
        val viewModel = AppsViewModel(
            settings = MutableStateFlow<TriSettings?>(null),
            initialApps = emptyList(),
            loadApps = { emptyList<AppInfo>() },
            setShowSystemApps = { _ -> },
            setRoute = { _, _ -> },
            restartTunnel = {},
            savedStateHandle = SavedStateHandle(mapOf("apps_query" to "browser")),
        )

        assertEquals("browser", viewModel.uiState.value.query)
    }

    @Test fun dnsCustomDraftAndEditModeRestoreFromSavedState() {
        val draft = "https://dns.example/dns-query"
        val viewModel = DnsViewModel(
            settings = MutableStateFlow<TriSettings?>(null),
            setDns = { _, _ -> },
            restartTunnel = {},
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "dns_custom_draft" to draft,
                    "dns_editing_custom" to true,
                ),
            ),
        )

        assertEquals(draft, viewModel.uiState.value.customField)
        assertTrue(viewModel.uiState.value.editingCustom)
    }

    @Test fun dpiCustomDraftAndEditModeRestoreFromSavedState() {
        val draft = "-d 1 -s 2"
        val viewModel = DpiViewModel(
            settings = MutableStateFlow<TriSettings?>(null),
            setPreset = { _ -> },
            setCustomArgs = { _ -> },
            setAutoDomainPlan = { _ -> },
            vpnState = MutableStateFlow(VpnState.Idle),
            runAutoSearch = { _, _ -> error("not used by saved-state test") },
            restartTunnel = {},
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "dpi_custom_draft" to draft,
                    "dpi_editing_custom" to true,
                ),
            ),
        )

        assertEquals(draft, viewModel.uiState.value.customField)
        assertTrue(viewModel.uiState.value.editingCustom)
    }
}
