package dev.triplet.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.triplet.app.R

/**
 * Single-choice list row with radio semantics and a branded square selection mark.
 * Selection is conveyed by both the tonal surface and a non-color check icon.
 */
@Composable
fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = detourColors
    val haptics = LocalHapticFeedback.current

    Row(
        modifier
            .fillMaxWidth()
            .detourSelectable(
                selected = selected,
                onClick = {
                    if (!selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClick()
                    }
                },
                idleColor = if (selected) c.accentSoft else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
                pressScale = Motion.PRESS_RADIO,
            )
            .heightIn(min = 60.dp)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Column(Modifier.padding(start = Spacing.space12).weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Spacing.space2),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val c = detourColors
    Box(
        modifier = modifier
            .size(24.dp)
            .background(
                color = if (selected) c.accent else c.surfaceSoft,
                shape = AppShapes.extraSmall,
            )
            .border(
                width = 1.dp,
                color = if (selected) c.accent else c.border,
                shape = AppShapes.extraSmall,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(
                animationSpec = spring(
                    dampingRatio = Motion.SPRING_DAMPING,
                    stiffness = Motion.SPRING_STIFFNESS,
                ),
                initialScale = 0.82f,
            ),
            exit = fadeOut() + scaleOut(targetScale = 0.82f),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = c.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
