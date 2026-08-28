package dev.triplet.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.triplet.app.R

val detourColors: DetourColors
    @Composable get() = LocalDetourTheme.current.colors

val hairline: Color
    @Composable get() = detourColors.border

/**
 * Click semantics for flat custom surfaces without a transient overlay.
 * Selected/idle color is stable; interaction feedback is carried by the
 * control's actual state change rather than a flash-like press tint.
 */
@Composable
fun Modifier.detourClickable(
    onClick: () -> Unit,
    role: Role? = null,
    idleColor: Color = Color.Transparent,
    pressedColor: Color? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .background(idleColor)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            role = role,
            onClick = onClick,
        )
}

@Composable
fun DetourCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = detourColors
    Column(
        modifier
            .fillMaxWidth()
            .clip(AppShapes.medium)
            .background(c.surface)
            .border(1.dp, c.border, AppShapes.medium),
        content = content,
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String?,
    iconRes: Int,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = detourColors
    Row(
        Modifier.fillMaxWidth()
            .detourClickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 58.dp)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).background(c.accentSoft, AppShapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes), null,
                tint = c.accent,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        trailing?.invoke() ?: Chevron()
    }
}

@Composable
fun Chevron() {
    Text(
        "›", style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        color = detourColors.textMuted.copy(alpha = .9f),
        modifier = Modifier.padding(start = Spacing.space4),
    )
}

@Composable
fun GroupDivider(startInset: Int = 60) {
    Box(
        Modifier.fillMaxWidth()
            .padding(start = startInset.dp, end = Spacing.space16)
            .height(1.dp)
            .background(detourColors.divider),
    )
}

@Composable
fun DetourSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    animate: Boolean = true,
    compact: Boolean = false,
) {
    val c = detourColors
    val trackWidth = if (compact) 40.dp else 44.dp
    val trackHeight = if (compact) 24.dp else 26.dp
    val thumbSize = if (compact) 18.dp else 20.dp
    val thumbMargin = 3.dp
    val targetOffset = if (checked) trackWidth - thumbSize - thumbMargin else thumbMargin
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = tween(100),
        label = "switchThumb",
    )
    val thumbOffset = if (animate) animatedOffset else targetOffset
    val trackColor by animateColorAsState(
        if (checked) c.accent else c.border.copy(alpha = .85f),
        tween(100), label = "switchTrack",
    )
    val trackBorder by animateColorAsState(
        if (checked) c.accent else c.textMuted.copy(alpha = .25f),
        tween(100), label = "switchBorder",
    )
    val thumbBorder by animateColorAsState(
        if (checked) Color.Transparent else c.textMuted.copy(alpha = .25f),
        tween(100), label = "switchThumbBorder",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        Modifier.size(48.dp).toggleable(
            value = checked,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(trackWidth, trackHeight)
                .background(trackColor, androidx.compose.foundation.shape.CircleShape)
                .border(1.dp, trackBorder, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, thumbBorder, androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

enum class ButtonStyle { PRIMARY, SECONDARY }

@Composable
fun DetourButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    height: Int = 52,
    container: Color? = null,
    contentColor: Color? = null,
    disabledContainer: Color? = null,
    disabledContent: Color? = null,
    borderColor: Color? = null,
    elevation: ButtonElevation? = null,
) {
    val c = detourColors
    val bg = container ?: if (style == ButtonStyle.PRIMARY) c.accent else c.surface
    val fg = contentColor ?: if (style == ButtonStyle.PRIMARY) c.onAccent else c.textPrimary
    val disBg = disabledContainer ?: if (style == ButtonStyle.PRIMARY) c.accentSoft else c.surfaceSoft
    val disFg = disabledContent ?: if (style == ButtonStyle.PRIMARY) c.accent else c.textMuted
    val border = when {
        borderColor != null -> androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        style == ButtonStyle.SECONDARY -> androidx.compose.foundation.BorderStroke(1.dp, c.border)
        else -> null
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = AppShapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg,
            disabledContainerColor = disBg,
            disabledContentColor = disFg,
        ),
        elevation = elevation,
        border = border,
        modifier = modifier.fillMaxWidth().height(height.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = detourColors
    Row(
        modifier
            .fillMaxWidth()
            .detourClickable(
                onClick = onClick,
                role = Role.RadioButton,
                idleColor = if (selected) c.accentSoft else Color.Transparent,
                pressedColor = if (selected) c.accentSoft else c.surfaceSelected,
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Column(Modifier.padding(start = Spacing.space12).weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    val c = detourColors
    val ringColor by animateColorAsState(
        if (selected) c.accent else c.textMuted.copy(alpha = 0.55f),
        tween(90), label = "radioRing",
    )
    val dotSize by animateDpAsState(
        if (selected) 8.dp else 0.dp,
        tween(90), label = "radioDot",
    )
    Box(
        Modifier.size(18.dp).border(
            width = 1.5.dp,
            color = ringColor,
            shape = androidx.compose.foundation.shape.CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(dotSize).background(c.accent, androidx.compose.foundation.shape.CircleShape))
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = detourColors
    Row(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(AppShapes.extraSmall)
            .background(c.surfaceSoft)
            .border(1.dp, c.border, AppShapes.extraSmall),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val fg by animateColorAsState(
                if (on) c.accent else c.textSecondary,
                tween(90), label = "segFg",
            )
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .detourClickable(
                        onClick = { onSelect(i) },
                        role = Role.RadioButton,
                        idleColor = if (on) c.accentSoft else Color.Transparent,
                        pressedColor = if (on) c.accentSoft else c.surfaceSelected,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = fg,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = detourColors
    Row(
        modifier.fillMaxWidth().height(56.dp).padding(start = Spacing.space4, end = Spacing.space20),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painterResource(R.drawable.ic_back), stringResource(R.string.cd_back),
                tint = c.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(Spacing.space4))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = detourColors.surface,
    unfocusedContainerColor = detourColors.surface,
    disabledContainerColor = detourColors.surfaceSoft,
    focusedBorderColor = detourColors.accent,
    unfocusedBorderColor = detourColors.border,
    disabledBorderColor = detourColors.border,
    cursorColor = detourColors.accent,
    focusedTextColor = detourColors.textPrimary,
    unfocusedTextColor = detourColors.textPrimary,
    disabledTextColor = detourColors.textMuted,
)
