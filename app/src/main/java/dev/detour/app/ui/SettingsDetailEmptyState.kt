package dev.detour.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.detour.app.R

/** Empty detail pane used by adaptive settings layouts before a section is chosen. */
@Composable
fun SettingsDetailEmptyState(modifier: Modifier = Modifier) {
    val c = detourColors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .padding(Spacing.space32),
        contentAlignment = Alignment.Center,
    ) {
        DetourCard(
            Modifier.widthIn(max = 440.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space24, vertical = Spacing.space32),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(c.accentSoft, AppShapes.medium)
                        .border(
                            1.dp,
                            c.accentBorder.copy(alpha = 0.58f),
                            AppShapes.medium,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    DetourBrandMark(size = 44.dp)
                }

                Spacer(Modifier.height(Spacing.space20))
                Text(
                    text = stringResource(R.string.settings_select_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(Spacing.space20))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        Spacing.space8,
                        Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetourRouteChip(
                        text = stringResource(R.string.route_direct),
                        iconRes = R.drawable.ic_globe,
                        selected = false,
                        modifier = Modifier.weight(1f),
                    )
                    DetourRouteChip(
                        text = stringResource(R.string.route_vpn),
                        iconRes = R.drawable.ic_lock,
                        selected = true,
                        modifier = Modifier.weight(1f),
                    )
                    DetourRouteChip(
                        text = stringResource(R.string.route_dpi),
                        iconRes = R.drawable.ic_dpi,
                        selected = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
