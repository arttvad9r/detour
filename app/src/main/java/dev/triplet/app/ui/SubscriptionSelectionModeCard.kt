package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.triplet.app.R
import dev.triplet.app.core.SubscriptionSelectionMode

@Composable
internal fun SubscriptionSelectionModeCard(
    mode: SubscriptionSelectionMode,
    currentNode: String?,
    onModeChange: (SubscriptionSelectionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val modes = listOf(SubscriptionSelectionMode.AUTO, SubscriptionSelectionMode.MANUAL)
    DetourCard(modifier) {
        Column(
            Modifier.padding(
                horizontal = Spacing.space12,
                vertical = Spacing.space12,
            ),
        ) {
            Text(
                text = stringResource(R.string.subscription_selection_mode),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.space4),
            )
            Spacer(Modifier.height(Spacing.space8))
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.subscription_selection_auto),
                    stringResource(R.string.subscription_selection_manual),
                ),
                selected = modes.indexOf(mode).coerceAtLeast(0),
                onSelect = { index -> modes.getOrNull(index)?.let(onModeChange) },
            )
            Spacer(Modifier.height(Spacing.space8))
            Text(
                text = when (mode) {
                    SubscriptionSelectionMode.AUTO -> currentNode?.let {
                        stringResource(R.string.subscription_selection_auto_current, it)
                    } ?: stringResource(R.string.subscription_selection_auto_hint)
                    SubscriptionSelectionMode.MANUAL ->
                        stringResource(R.string.subscription_selection_manual_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.space4),
            )
        }
    }
}
