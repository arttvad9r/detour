package dev.triplet.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.triplet.app.R

val detourColors: DetourColors
    @Composable get() = LocalDetourColors.current

val hairline: Color
    @Composable get() = detourColors.border

/**
 * Flat interaction feedback without a ripple flash. Large surfaces use tonal
 * feedback only; small controls may opt into a restrained press scale.
 */
@Composable
fun Modifier.detourClickable(
    onClick: () -> Unit,
    role: Role? = null,
    idleColor: Color = Color.Transparent,
    pressedColor: Color? = null,
    pressScale: Float = 1f,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING,
            stiffness = Motion.SPRING_STIFFNESS,
        ),
        label = "pressScale",
    )
    val background by animateColorAsState(
        targetValue = if (pressed && pressedColor != null) pressedColor else idleColor,
        animationSpec = tween(Motion.PRESS_TONE_MS),
        label = "pressTone",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .background(background)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            role = role,
            onClick = onClick,
        )
}

/** Radio-style feedback with selected semantics for accessibility services. */
@Composable
fun Modifier.detourSelectable(
    selected: Boolean,
    onClick: () -> Unit,
    idleColor: Color = Color.Transparent,
    pressedColor: Color? = null,
    pressScale: Float = 1f,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING,
            stiffness = Motion.SPRING_STIFFNESS,
        ),
        label = "selectScale",
    )
    val background by animateColorAsState(
        targetValue = if (pressed && pressedColor != null) pressedColor else idleColor,
        animationSpec = tween(Motion.PRESS_TONE_MS),
        label = "selectTone",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .background(background)
        .selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = null,
            role = Role.RadioButton,
            onClick = onClick,
        )
}

/** Switch-row feedback with one toggleable semantics node for the whole row. */
@Composable
fun Modifier.detourToggleable(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    idleColor: Color = Color.Transparent,
    pressedColor: Color? = null,
    pressScale: Float = 1f,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING,
            stiffness = Motion.SPRING_STIFFNESS,
        ),
        label = "toggleScale",
    )
    val background by animateColorAsState(
        targetValue = if (pressed && pressedColor != null) pressedColor else idleColor,
        animationSpec = tween(Motion.PRESS_TONE_MS),
        label = "toggleTone",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .background(background)
        .toggleable(
            value = value,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Switch,
            onValueChange = onValueChange,
        )
}

/** Small controls yield slightly under the finger without moving surrounding layout. */
@Composable
fun DetourIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 48,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESS_ICON else 1f,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING,
            stiffness = Motion.SPRING_STIFFNESS,
        ),
        label = "iconPress",
    )
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(size.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
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
            .detourClickable(
                onClick = onClick,
                role = Role.Button,
                pressedColor = c.surfaceSelected.copy(alpha = 0.38f),
                pressScale = Motion.PRESS_ROW,
            )
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
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        fadeIn(tween(Motion.CONTENT_IN_MS)) togetherWith
                            fadeOut(tween(Motion.CONTENT_OUT_MS))
                    },
                    label = "settingSubtitle",
                ) { value ->
                    Text(
                        value, style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
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
    onCheckedChange: ((Boolean) -> Unit)?,
    animate: Boolean = true,
    compact: Boolean = false,
) {
    val c = detourColors
    val haptics = LocalHapticFeedback.current
    val trackWidth = if (compact) 40.dp else 44.dp
    val trackHeight = if (compact) 24.dp else 26.dp
    val thumbSize = if (compact) 18.dp else 20.dp
    val thumbMargin = 3.dp
    val targetOffset = if (checked) trackWidth - thumbSize - thumbMargin else thumbMargin
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING,
            stiffness = Motion.SPRING_STIFFNESS_SOFT,
        ),
        label = "switchThumb",
    )
    val thumbOffset = if (animate) animatedOffset else targetOffset
    val trackColor by animateColorAsState(
        if (checked) c.accent else c.border.copy(alpha = .85f),
        tween(Motion.COLOR_MS), label = "switchTrack",
    )
    val trackBorder by animateColorAsState(
        if (checked) c.accent else c.textMuted.copy(alpha = .25f),
        tween(Motion.COLOR_MS), label = "switchBorder",
    )
    val thumbBorder by animateColorAsState(
        if (checked) Color.Transparent else c.textMuted.copy(alpha = .25f),
        tween(Motion.COLOR_MS), label = "switchThumbBorder",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val switchModifier = if (onCheckedChange == null) {
        Modifier.size(48.dp)
    } else {
        Modifier.size(48.dp).toggleable(
            value = checked,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Switch,
            onValueChange = { value ->
                haptics.performHapticFeedback(
                    if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
                onCheckedChange(value)
            },
        )
    }

    Box(
        switchModifier,
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) Motion.PRESS_BUTTON else 1f,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING,
            stiffness = Motion.SPRING_STIFFNESS,
        ),
        label = "buttonPress",
    )
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
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                fadeIn(tween(Motion.CONTENT_IN_MS)) togetherWith
                    fadeOut(tween(Motion.CONTENT_OUT_MS))
            },
            label = "buttonText",
        ) { label ->
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
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
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = {
                        fadeIn(tween(Motion.CONTENT_IN_MS)) togetherWith
                            fadeOut(tween(Motion.CONTENT_OUT_MS))
                    },
                    label = "radioSubtitle",
                ) { value ->
                    Text(
                        value, style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
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
        tween(Motion.COLOR_MS), label = "radioRing",
    )
    val dotSize by animateDpAsState(
        if (selected) 8.dp else 0.dp,
        spring(dampingRatio = Motion.SPRING_DAMPING, stiffness = Motion.SPRING_STIFFNESS),
        label = "radioDot",
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
    val haptics = LocalHapticFeedback.current
    if (options.isEmpty()) return
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(AppShapes.extraSmall)
            .background(c.surfaceSoft)
            .border(1.dp, c.border, AppShapes.extraSmall),
    ) {
        val segmentWidth = maxWidth / options.size
        val selectedOffset by animateDpAsState(
            targetValue = segmentWidth * selected.coerceIn(0, options.lastIndex),
            animationSpec = spring(
                dampingRatio = Motion.SPRING_DAMPING,
                stiffness = Motion.SPRING_STIFFNESS_SOFT,
            ),
            label = "segmentOffset",
        )
        Box(
            Modifier
                .offset(x = selectedOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(1.dp)
                .clip(AppShapes.extraSmall)
                .background(c.accentSoft),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val fg by animateColorAsState(
                    if (on) c.accent else c.textSecondary,
                    tween(Motion.COLOR_MS), label = "segFg",
                )
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .detourSelectable(
                            selected = on,
                            onClick = {
                                if (i != selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(i)
                                }
                            },
                            pressScale = Motion.PRESS_RADIO,
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
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = detourColors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Spacing.space4, end = Spacing.space20),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetourIconButton(onClick = onBack) {
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
