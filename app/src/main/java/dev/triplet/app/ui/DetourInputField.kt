package dev.triplet.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private enum class SupportingTone { ERROR, SUCCESS, HELPER }
private data class SupportingText(val text: String, val tone: SupportingTone)

/**
 * Shared input treatment with restrained focus/validation motion. Multiline
 * fields still grow only with their actual content; no decorative bouncing.
 */
@Composable
fun DetourInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    success: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 56.dp,
    maxHeight: Dp = if (singleLine) 56.dp else 160.dp,
    maxLines: Int = if (singleLine) 1 else 6,
    monospace: Boolean = false,
) {
    val c = detourColors
    var focused by remember { mutableStateOf(false) }
    val targetBorder = when {
        error != null -> c.error
        focused -> c.accent
        else -> c.border
    }
    val targetLabel = when {
        error != null -> c.error
        focused -> c.accent
        else -> c.textSecondary
    }
    val borderColor by animateColorAsState(targetBorder, tween(Motion.COLOR_MS), label = "fieldBorder")
    val labelColor by animateColorAsState(targetLabel, tween(Motion.COLOR_MS), label = "fieldLabel")
    val baseTextStyle = MaterialTheme.typography.bodyLarge
    val textStyle = if (monospace) {
        baseTextStyle.copy(color = c.textPrimary, fontFamily = FontFamily.Monospace)
    } else {
        baseTextStyle.copy(color = c.textPrimary)
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier.padding(start = Spacing.space4, bottom = Spacing.space8),
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight)
                .onFocusChanged { focused = it.isFocused }
                .background(c.surface, AppShapes.small)
                .border(1.dp, borderColor, AppShapes.small)
                .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = textStyle,
            cursorBrush = SolidColor(c.accent),
            decorationBox = { innerTextField ->
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AnimatedContent(
                        targetState = value.isEmpty(),
                        transitionSpec = {
                            fadeIn(tween(Motion.CONTENT_IN_MS)) togetherWith
                                fadeOut(tween(Motion.CONTENT_OUT_MS))
                        },
                        label = "fieldPlaceholder",
                    ) { empty ->
                        if (empty) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = c.textMuted,
                                maxLines = 1,
                            )
                        } else {
                            Box(Modifier)
                        }
                    }
                    innerTextField()
                }
            },
        )

        val supporting = when {
            error != null -> SupportingText(error, SupportingTone.ERROR)
            success != null -> SupportingText(success, SupportingTone.SUCCESS)
            helper != null -> SupportingText(helper, SupportingTone.HELPER)
            else -> null
        }
        AnimatedContent(
            targetState = supporting,
            transitionSpec = {
                fadeIn(tween(Motion.CONTENT_IN_MS, delayMillis = 20)) togetherWith
                    fadeOut(tween(Motion.CONTENT_OUT_MS))
            },
            label = "fieldSupporting",
        ) { shown ->
            if (shown != null) {
                Text(
                    text = shown.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (shown.tone) {
                        SupportingTone.ERROR -> c.error
                        SupportingTone.SUCCESS -> c.textSecondary
                        SupportingTone.HELPER -> c.textMuted
                    },
                    modifier = Modifier.padding(
                        start = Spacing.space4,
                        end = Spacing.space4,
                        top = Spacing.space8,
                    ),
                )
            } else {
                Box(Modifier)
            }
        }
    }
}
