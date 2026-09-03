package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val NavigationRowDividerInset = 58

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
        .heightIn(min = 56.dp)
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
        rowModifier.padding(horizontal = Spacing.space12, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(c.accentSoft, AppShapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(
            Modifier
                .padding(start = Spacing.space12)
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
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
        }
        trailing?.invoke() ?: if (onClick != null) Chevron()
    }
}
