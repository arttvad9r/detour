package dev.triplet.app.ui

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

/**
 * Shared Detour input treatment. Labels stay outside the border so short fields,
 * multiline technical values and focused fields keep the same geometry.
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
    minHeight: Dp = if (singleLine) 56.dp else 112.dp,
    maxLines: Int = if (singleLine) 1 else 6,
    monospace: Boolean = false,
) {
    val c = detourColors
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        error != null -> c.error
        focused -> c.accent
        else -> c.border
    }
    val labelColor = when {
        error != null -> c.error
        focused -> c.accent
        else -> c.textSecondary
    }
    val textStyle = (if (monospace) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge)
        .copy(
            color = c.textPrimary,
            fontFamily = if (monospace) FontFamily.Monospace else MaterialTheme.typography.bodyLarge.fontFamily,
        )

    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier.padding(start = Spacing.space4, bottom = Spacing.space8),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(c.surface, AppShapes.small)
                .border(1.dp, borderColor, AppShapes.small)
                .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = textStyle,
                cursorBrush = SolidColor(c.accent),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = c.textMuted,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        val supporting = error ?: success ?: helper
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    error != null -> c.error
                    success != null -> c.textSecondary
                    else -> c.textMuted
                },
                modifier = Modifier.padding(
                    start = Spacing.space4,
                    end = Spacing.space4,
                    top = Spacing.space8,
                ),
            )
        }
    }
}
