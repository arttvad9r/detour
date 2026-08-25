package dev.triplet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Волосная граница карточек: слабый lavender-gray. */
@Composable
fun hairline(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

/**
 * Базовая карточка: светлая поверхность, radius 22, тонкая граница, без тени.
 */
@Composable
fun DetourCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, AppShapes.large)
            .border(1.dp, hairline(), AppShapes.large)
            .padding(vertical = 4.dp),
        content = content,
    )
}

/**
 * Строка настройки: line-иконка на слабом accent-фоне, заголовок, подпись,
 * шеврон. Единый accent-цвет иконок для всех разделов.
 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String?,
    iconRes: Int,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                RoundedCornerShape(12.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes), null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        trailing?.invoke() ?: Text(
            "›", fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Разделитель внутри карточки с отступом от иконки. */
@Composable
fun CardDivider() = Row(
    Modifier.fillMaxWidth().padding(start = 64.dp, end = 14.dp),
) {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    )
}

/** Широкая pill-кнопка главного действия (52–56dp, без иконки питания). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    disabledContainer: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
    disabledContent: Color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent,
        ),
        modifier = modifier.fillMaxWidth().height(54.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    }
}

/**
 * Выбираемая строка-опция (radio): выбранная — soft lavender, остальные —
 * прозрачные/светлые, без тяжёлых границ.
 */
@Composable
fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surface,
                AppShapes.medium,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else hairline(),
                AppShapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).border(
                2.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                androidx.compose.foundation.shape.CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier.size(10.dp).background(
                        MaterialTheme.colorScheme.primary,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
                )
            }
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(
                title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
