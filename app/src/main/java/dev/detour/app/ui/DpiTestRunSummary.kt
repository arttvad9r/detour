package dev.detour.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.detour.app.R
import dev.detour.app.core.DpiProxyTestCatalog
import dev.detour.app.core.DpiProxyTestRun
import dev.detour.app.core.bestCandidate

/** The recommendation belongs to this saved run, not the current setup controls. */
@Composable
internal fun DpiTestRunSummary(
    run: DpiProxyTestRun,
    applyingId: String?,
    appliedId: String?,
    applyErrorId: String?,
    enabled: Boolean,
    onApply: (String) -> Unit,
) {
    val best = run.bestCandidate()
    val c = detourColors
    DetourCard(Modifier.padding(horizontal = Spacing.space16).testTag("dpi-run-summary")) {
        Column(Modifier.padding(Spacing.space12), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Text(stringResource(when {
                best == null -> R.string.dpi_result_none
                best.fullCoverage -> R.string.dpi_result_all
                else -> R.string.dpi_result_partial
            }), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            best?.services?.forEach { service ->
                val name = dpiServiceLabel(service.serviceId)
                if (name.isNotBlank()) {
                    Text(stringResource(R.string.dpi_result_service, name, service.fullyWorkingHosts,
                        service.hostCount, service.successes, service.attempts),
                        style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
            }
            Text(stringResource(R.string.dpi_result_probe_short), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            if (best != null) {
                val applying = applyingId == best.strategy.id
                val applied = appliedId == best.strategy.id
                DetourButton(
                    text = stringResource(when {
                        applying -> R.string.dpi_proxy_test_applying
                        applied -> R.string.dpi_proxy_test_applied
                        else -> R.string.dpi_result_apply_best
                    }),
                    enabled = enabled && applyingId == null && !applied,
                    onClick = { onApply(best.strategy.id) },
                    modifier = Modifier.testTag("dpi-apply-best"),
                )
                if (applyErrorId == best.strategy.id) {
                    Text(stringResource(R.string.dpi_proxy_test_apply_error), color = c.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            DetourDetails(stringResource(R.string.dpi_result_explanation)) {
                Text(stringResource(R.string.dpi_result_ranking), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Text(stringResource(if (run.config.attemptsPerHost > 1) R.string.dpi_result_repeated else R.string.dpi_result_single,
                    run.config.attemptsPerHost), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Text(stringResource(R.string.dpi_result_probe_limit), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
}
