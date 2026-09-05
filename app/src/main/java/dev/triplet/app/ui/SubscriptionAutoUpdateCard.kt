package dev.triplet.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.triplet.app.R
import dev.triplet.app.core.SubscriptionRefreshPolicy
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SubscriptionAutoUpdateCard(
    intervalHours: Int?,
    providerRecommendedHours: Int?,
    updatedAt: Long?,
    onIntervalChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    val enabled = intervalHours != null
    val updatedText = updatedAt?.takeIf { it > 0L }?.let { millis ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
    }
    val suggested = SubscriptionRefreshPolicy.suggestedIntervalHours(providerRecommendedHours)
    val options = linkedSetOf(suggested, 6, 12, 24).filter { it > 0 }

    DetourCard(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .detourToggleable(
                    value = enabled,
                    onValueChange = { next ->
                        onIntervalChange(if (next) suggested else null)
                    },
                    pressedColor = c.surfaceSelected.copy(alpha = 0.34f),
                    pressScale = Motion.PRESS_ROW,
                )
                .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.subscription_auto_update),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                )
                Text(
                    text = updatedText?.let {
                        stringResource(R.string.subscription_last_updated, it)
                    } ?: stringResource(R.string.subscription_not_updated_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
            DetourSwitch(
                checked = enabled,
                onCheckedChange = null,
                compact = true,
            )
        }
        if (enabled) {
            GroupDivider(startInset = 0.dp)
            Column(
                Modifier.padding(
                    start = Spacing.space12,
                    end = Spacing.space12,
                    top = Spacing.space8,
                    bottom = Spacing.space8,
                ),
            ) {
                Text(
                    text = stringResource(R.string.subscription_update_interval),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    options.forEach { hours ->
                        TextButton(onClick = { onIntervalChange(hours) }) {
                            Text(
                                text = stringResource(R.string.subscription_interval_hours, hours),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (intervalHours == hours) c.accent else c.textSecondary,
                            )
                        }
                    }
                }
                if (providerRecommendedHours != null) {
                    Text(
                        text = stringResource(
                            R.string.subscription_provider_recommends_interval,
                            providerRecommendedHours,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted,
                        modifier = Modifier.padding(horizontal = Spacing.space4),
                    )
                }
            }
        }
    }
}
