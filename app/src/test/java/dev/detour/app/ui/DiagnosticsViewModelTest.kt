package dev.detour.app.ui

import dev.detour.app.core.VpnProfileKind
import dev.detour.app.vpn.VpnState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsViewModelTest {
    @Test
    fun `redactor removes urls uuids and secret assignments`() {
        val raw = "failed https://sub.example/token?auth=secret " +
            "uuid=123e4567-e89b-12d3-a456-426614174000 " +
            "private-key=abcdefghijklmnopqrstuvwxyzABCDEFGH"

        val safe = redactDiagnosticText(raw)

        assertFalse(safe.contains("sub.example"))
        assertFalse(safe.contains("123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(safe.contains("abcdefghijklmnopqrstuvwxyzABCDEFGH"))
        assertTrue(safe.contains("[redacted"))
    }

    @Test
    fun `support report contains operational state latency and multi-hop without free text secrets`() {
        val report = buildDiagnosticsReport(
            DiagnosticsUiState(
                loading = false,
                vpnPermissionGranted = true,
                vpnState = VpnState.Active,
                engineReady = true,
                vpnProbe = DiagnosticProbeState.PASS,
                dpiProbe = DiagnosticProbeState.NOT_APPLICABLE,
                vpnLatencyMs = 48L,
                profileKind = VpnProfileKind.SUBSCRIPTION,
                profileName = "Primary",
                serverLabel = "node-1",
                multiHopEnabled = true,
                multiHopEntryLabel = "Entry",
                multiHopValid = true,
                dnsId = "cloudflare",
                dnsValid = true,
                vpnRouteCount = 3,
                dpiRouteCount = 0,
                lastError = "token=abcdefghijklmnopqrstuvwxyzABCDEFGH",
                checkedAt = 123L,
            ),
        )

        assertTrue(report.contains("vpn_state=ACTIVE"))
        assertTrue(report.contains("vpn_probe=PASS"))
        assertTrue(report.contains("vpn_latency_ms=48"))
        assertTrue(report.contains("vpn_routes=3"))
        assertTrue(report.contains("multi_hop_enabled=true"))
        assertTrue(report.contains("multi_hop_valid=true"))
        assertTrue(report.contains("multi_hop_entry=Entry"))
        assertFalse(report.contains("abcdefghijklmnopqrstuvwxyzABCDEFGH"))
    }
}
