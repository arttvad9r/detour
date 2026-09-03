package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val NavigationRowDividerInset = 58
internal val NavigationRowMinHeight = 56.dp
internal val NavigationRowHorizontalPadding = Spacing.space12
internal val NavigationRowVerticalPadding = Spacing.space8
internal val NavigationRowLeadingTileSize = 34.dp
internal val NavigationRowLeadingIconSize = 17.dp
internal val NavigationRowContentGap = Spacing.space12
internal val NavigationRowSubtitleGap = Spacing.space2

@Composable
fun DetourNavigationRow(
    title: String,
    subtitle: String?,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selectedBackground: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = detourColors
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = NavigationRowMinHeight)
    val rowModifier = if (onClick != null) {
        base.detourClickable(
            onClick = onClick,
            role = androidx.compose.ui.semantics.Role.Button,
            idleColor = if (selectedBackground) c.accentSoft else Color.Transparent,
            pressedColor = c.surfaceSelected.copy(alpha = 0.38f),
            pressScale = Motion.PRESS_ROW,
        )
    } else {
        base.background(if (selectedBackground) c.accentSoft else Color.Transparent)
    }

    Row(
        rowModifier.padding(
            horizontal = NavigationRowHorizontalPadding,
            vertical = NavigationRowVerticalPadding,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetourIconTile(
            iconRes = iconRes,
            selected = true,
            size = NavigationRowLeadingTileSize,
            iconSize = NavigationRowLeadingIconSize,
            bordered = false,
        )
        Column(
            Modifier
                .padding(start = NavigationRowContentGap)
                .weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = NavigationRowSubtitleGap),
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Chevron()
        }
    }
}
