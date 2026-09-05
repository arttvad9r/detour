package dev.triplet.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.triplet.app.R
import dev.triplet.app.core.VpnProfileKind
import dev.triplet.app.vpn.VpnState
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val c = detourColors

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .detourHighRefresh(scrollState.isScrollInProgress),
    ) {
        DetourBrandedHeader(stringResource(R.string.diagnostics_title), onBack)

        DetourContentColumn {
            Spacer(Modifier.height(Spacing.space4))

            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                DiagnosticsSectionTitle(stringResource(R.string.diagnostics_runtime_section))
                DiagnosticRow(
                    stringResource(R.string.diagnostics_vpn_permission),
                    stringResource(
                        if (state.vpnPermissionGranted) R.string.diagnostics_granted
                        else R.string.diagnostics_not_granted,
                    ),
                    good = state.vpnPermissionGranted,
                    bad = !state.vpnPermissionGranted,
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DiagnosticRow(
                    stringResource(R.string.diagnostics_vpn_service),
                    vpnStateLabel(state.vpnState),
                    good = state.vpnState == VpnState.Active,
                    bad = state.vpnState is VpnState.Failed,
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DiagnosticRow(
                    stringResource(R.string.diagnostics_engine),
                    stringResource(
                        if (state.engineReady) R.string.diagnostics_ready
                        else R.string.diagnostics_not_ready,
                    ),
                    good = state.engineReady,
                    bad = state.vpnState == VpnState.Active && !state.engineReady,
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DiagnosticRow(
                    stringResource(R.string.diagnostics_vpn_route),
                    probeLabel(state.vpnProbe),
                    good = state.vpnProbe == DiagnosticProbeState.PASS,
                    bad = state.vpnProbe == DiagnosticProbeState.FAIL,
                )
                state.vpnLatencyMs?.let { latencyMs ->
                    GroupDivider(startInset = NavigationRowDividerInset)
                    DiagnosticRow(
                        stringResource(R.string.diagnostics_vpn_latency),
                        stringResource(R.string.diagnostics_latency_value, latencyMs),
                        good = true,
                    )
                }
                GroupDivider(startInset = NavigationRowDividerInset)
                DiagnosticRow(
                    stringResource(R.string.diagnostics_dpi_route),
                    probeLabel(state.dpiProbe),
                    good = state.dpiProbe == DiagnosticProbeState.PASS,
                    bad = state.dpiProbe == DiagnosticProbeState.FAIL,
                )
            }

            Spacer(Modifier.height(Spacing.space12))
            DetourButton(
                text = stringResource(
                    if (state.checking) R.string.diagnostics_running else R.string.diagnostics_run,
                ),
                onClick = { viewModel.refresh(runProbes = true) },
                enabled = !state.loading && !state.checking,
                modifier = Modifier.padding(horizontal = Spacing.space16),
            )

            Spacer(Modifier.height(Spacing.space16))
            DetourCard(Modifier.padding(horizontal = Spacing.space16)) {
                DiagnosticsSectionTitle(stringResource(R.string.diagnostics_configuration_section))
                DiagnosticRow(
                    stringResource(R.string.diagnostics_profile),
                    state.profileName ?: profileKindLabel(state.profileKind),
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DiagnosticRow(
                    stringResource(R.string.diagnostics_server),
                    state.serverLabel ?: when {
                        state.endpointCount > 0 -> stringResource(
                            R.string.diagnostics_endpoints,
                            state.endpointCount,
                        )
                        else -> stringResource(R.string.diagnostics_no_value)
                    },
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DiagnosticRow(
                    stringResource(R.string.diagnostics_multi_hop),
                    when {
                        !state.multiHopEnabled -> stringResource(R.string.diagnostics_disabled)
                        !state.multiHopValid -> stringResource(R.string.diagnostics_unavailable)
                        else -> state.multiHopEntryLabel ?: stringResource(R.string.diagnostics_no_value)
                    },
                    good = state.multiHopEnabled && state.multiHopValid,
                    bad = state.multiHopEnabled && !state.multiHopValid,
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DiagnosticRow(
                    stringResource(R.string.diagnostics_dns),
                    dnsLabel(state.dnsId),
                    good = state.dnsValid,
                    bad = !state.dnsValid,
                )
                GroupDivider(startInset = NavigationRowDividerInset)
                DiagnosticRow(
                    stringResource(R.string.diagnostics_routes),
                    stringResource(
                        R.string.diagnostics_routes_value,
                        state.vpnRouteCount,
                        state.dpiRouteCount,
                    ),
                )
                state.checkedAt?.let { checkedAt ->
                    GroupDivider(startInset = NavigationRowDividerInset)
                    DiagnosticRow(
                        stringResource(R.string.diagnostics_checked),
                        DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(checkedAt)),
                    )
                }
                state.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                    GroupDivider(startInset = NavigationRowDividerInset)
                    DiagnosticRow(
                        stringResource(R.string.diagnostics_last_error),
                        error,
                        bad = true,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.space12))
            DetourButton(
                text = stringResource(R.string.diagnostics_copy),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Detour diagnostics", viewModel.report()),
                    )
                    Toast.makeText(context, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
                },
                enabled = !state.loading,
                modifier = Modifier.padding(horizontal = Spacing.space16),
                container = c.surface,
                contentColor = c.textPrimary,
                borderColor = c.border,
            )
            Spacer(Modifier.height(Spacing.space16))
        }
    }
}

@Composable
private fun DiagnosticsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = detourColors.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.space12,
            end = Spacing.space12,
            top = Spacing.space8,
            bottom = Spacing.space4,
        ),
    )
}

@Composable
private fun DiagnosticRow(
    title: String,
    value: String,
    good: Boolean = false,
    bad: Boolean = false,
) {
    val c = detourColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (good || bad) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                bad -> c.error
                good -> c.accent
                else -> c.textSecondary
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        )
    }
}

@Composable
private fun probeLabel(state: DiagnosticProbeState): String = stringResource(
    when (state) {
        DiagnosticProbeState.NOT_RUN -> R.string.diagnostics_not_run
        DiagnosticProbeState.RUNNING -> R.string.diagnostics_running
        DiagnosticProbeState.PASS -> R.string.diagnostics_pass
        DiagnosticProbeState.FAIL -> R.string.diagnostics_fail
        DiagnosticProbeState.NOT_APPLICABLE -> R.string.diagnostics_not_applicable
    },
)

@Composable
private fun vpnStateLabel(state: VpnState): String = stringResource(
    when (state) {
        VpnState.Idle -> R.string.diagnostics_vpn_idle
        VpnState.Starting -> R.string.diagnostics_vpn_starting
        VpnState.Active -> R.string.diagnostics_vpn_active
        is VpnState.Failed -> R.string.diagnostics_vpn_failed
    },
)

@Composable
private fun profileKindLabel(kind: VpnProfileKind): String = stringResource(
    when (kind) {
        VpnProfileKind.VLESS, VpnProfileKind.SUBSCRIPTION -> R.string.protocol_vless
        VpnProfileKind.WARP -> R.string.protocol_warp
    },
)

@Composable
private fun dnsLabel(id: String): String = stringResource(
    when (id) {
        "google", "" -> R.string.dns_google
        "cloudflare" -> R.string.dns_cloudflare
        "adguard" -> R.string.dns_adguard
        "custom" -> R.string.dns_custom
        else -> R.string.diagnostics_invalid
    },
)
