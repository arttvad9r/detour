package dev.detour.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultContentMaxWidth = 640.dp

/**
 * Keeps settings/form content readable on wide windows while preserving the
 * existing edge-to-edge screen chrome and phone layout.
 */
@Composable
fun DetourContentColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = DefaultContentMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content,
        )
    }
}
