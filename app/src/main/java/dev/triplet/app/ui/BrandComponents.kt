package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.triplet.app.R

@Composable
fun DetourBrandMark(
    modifier: Modifier = Modifier,
    tint: Color = detourColors.accent,
    size: Dp = 34.dp,
) {
    Icon(
        painter = painterResource(R.drawable.ic_detour_mark),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

@Composable
fun DetourBrandWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        DetourBrandMark()
        Text(
            text = "Detour",
            style = MaterialTheme.typography.headlineSmall,
            color = detourColors.textPrimary,
        )
    }
}

@Composable
fun DetourIconTile(
    iconRes: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val c = detourColors
    Box(
        modifier = modifier
            .size(42.dp)
            .background(
                if (selected) c.accentSoft else c.surfaceSoft,
                AppShapes.extraSmall,
            )
            .border(
                1.dp,
                if (selected) c.accentBorder.copy(alpha = 0.65f) else c.border.copy(alpha = 0.72f),
                AppShapes.extraSmall,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (selected) c.accent else c.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun DetourFeatureSummary(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface, AppShapes.medium)
            .border(1.dp, c.border, AppShapes.medium)
            .padding(Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetourIconTile(iconRes = iconRes, selected = true)
        Column(
            modifier = Modifier
                .padding(start = Spacing.space12)
                .weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
        }
        DetourBrandMark(
            tint = c.accent.copy(alpha = 0.48f),
            size = 28.dp,
        )
    }
}

@Composable
fun DetourRouteChip(
    text: String,
    iconRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    Row(
        modifier = modifier
            .widthIn(min = 92.dp)
            .background(
                if (selected) c.accentSoft else c.surface,
                PillShape,
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) c.accent else c.border,
                PillShape,
            )
            .padding(horizontal = Spacing.space12, vertical = Spacing.space8),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (selected) c.accent else c.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) c.accent else c.textSecondary,
            modifier = Modifier.padding(start = Spacing.space8),
        )
    }
}
