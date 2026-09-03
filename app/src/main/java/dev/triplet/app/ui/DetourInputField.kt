package dev.triplet.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private enum class SupportingTone { ERROR, SUCCESS, HELPER }
private data class SupportingText(val text: String, val tone: SupportingTone)

/**
 * Shared Detour input treatment. Focus is communicated through a restrained
 * violet surface/border shift while validation remains semantic and readable.
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
    val targetContainer = when {
        error != null -> c.errorSoft.copy(alpha = 0.38f)
        focused -> c.accentSoft.copy(alpha = 0.54f)
        else -> c.surface
    }
    val borderColor by animateColorAsState(targetBorder, tween(Motion.COLOR_MS), label = "fieldBorder")
    val labelColor by animateColorAsState(targetLabel, tween(Motion.COLOR_MS), label = "fieldLabel")
    val containerColor by animateColorAsState(targetContainer, tween(Motion.COLOR_MS), label = "fieldContainer")
    val borderWidth by animateDpAsState(
        targetValue = if (focused || error != null) 1.5.dp else 1.dp,
        animationSpec = tween(Motion.COLOR_MS),
        label = "fieldBorderWidth",
    )
    val baseTextStyle = MaterialTheme.typography.bodyLarge
    val textStyle = if (monospace) {
        baseTextStyle.copy(color = c.textPrimary, fontFamily = FontFamily.Monospace)
    } else {
        baseTextStyle.copy(color = c.textPrimary)
    }
    val fieldHeightModifier = if (singleLine) {
        Modifier.heightIn(min = minHeight)
    } else {
        Modifier.heightIn(min = minHeight, max = maxHeight)
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
                .then(fieldHeightModifier)
                .semantics {
                    contentDescription = label
                    error?.let { message -> this.error(message) }
                }
                .onFocusChanged { focused = it.isFocused }
                .background(containerColor, AppShapes.small)
                .border(borderWidth, borderColor, AppShapes.small)
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
                                maxLines = if (singleLine) 1 else maxLines,
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
                        SupportingTone.SUCCESS -> c.activeStrong
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
