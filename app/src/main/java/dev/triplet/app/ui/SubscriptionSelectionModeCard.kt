package dev.triplet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    DetourCard(modifier) {
        Column(
            Modifier.padding(
                horizontal = Spacing.space12,
                vertical = Spacing.space10,
            ),
        ) {
            Text(
                text = stringResource(R.string.subscription_selection_mode),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.space4),
            )
            Row(Modifier.fillMaxWidth()) {
                SelectionModeButton(
                    text = stringResource(R.string.subscription_selection_auto),
                    selected = mode == SubscriptionSelectionMode.AUTO,
                    onClick = { onModeChange(SubscriptionSelectionMode.AUTO) },
                    modifier = Modifier.weight(1f),
                )
                SelectionModeButton(
                    text = stringResource(R.string.subscription_selection_manual),
                    selected = mode == SubscriptionSelectionMode.MANUAL,
                    onClick = { onModeChange(SubscriptionSelectionMode.MANUAL) },
                    modifier = Modifier.weight(1f),
                )
            }
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

@Composable
private fun SelectionModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    TextButton(
        onClick = onClick,
        enabled = !selected,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) c.accent else c.textSecondary,
        )
    }
}
