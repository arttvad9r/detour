package dev.triplet.app.ui

import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.vpn.VpnState
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
    fun `support report contains operational state but not free text secrets`() {
        val report = buildDiagnosticsReport(
            DiagnosticsUiState(
                loading = false,
                vpnPermissionGranted = true,
                vpnState = VpnState.Active,
                engineReady = true,
                vpnProbe = DiagnosticProbeState.PASS,
                dpiProbe = DiagnosticProbeState.NOT_APPLICABLE,
                profileKind = VpnProfileKind.SUBSCRIPTION,
                profileName = "Primary",
                serverLabel = "node-1",
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
        assertTrue(report.contains("vpn_routes=3"))
        assertFalse(report.contains("abcdefghijklmnopqrstuvwxyzABCDEFGH"))
    }
}
