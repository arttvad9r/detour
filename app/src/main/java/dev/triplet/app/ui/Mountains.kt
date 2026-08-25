package dev.triplet.app.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import dev.triplet.app.vpn.VpnState
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Слоистый горный пейзаж в нижней части экрана (только горы, без объектов).
 * Каждый слой — широкий массив с 2-3 отдельными вершинами и широкими
 * долинами; дальние планы светлее и выше, у подножий — туман, передний
 * план самый читаемый. Состояния: Idle — статично; Starting — полоса
 * тумана плывёт между слоями + лёгкий parallax планов; Active — за ~750мс
 * контраст и зелёный тинт. Анимация — только translate/alpha.
 */
@Composable
fun MountainBackground(
    palette: MountainPalette,
    state: VpnState,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()

    // Цикл тумана/parallax: 0..1 за 5с, крутится только в Starting.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(state, reduced) {
        if (state == VpnState.Starting && !reduced) {
            while (true) {
                phase.snapTo(0f)
                phase.animateTo(1f, tween(5000, easing = LinearEasing))
            }
        } else {
            phase.snapTo(0f)
        }
    }
    // Переход в Active: контраст + зелёный тинт за 750мс, дальше статично.
    val active = remember { Animatable(if (state == VpnState.Active) 1f else 0f) }
    LaunchedEffect(state, reduced) {
        if (state == VpnState.Active) {
            active.animateTo(1f, tween(if (reduced) 0 else 750, easing = FastOutSlowInEasing))
        } else {
            active.snapTo(0f)
        }
    }

    val cache = remember { MountainPaths() }
    Spacer(
        modifier.drawBehind {
            val w = size.width
            val h = size.height
            val scene = cache.get(w, h, palette.layers.size)
            val p = phase.value
            val a = active.value

            scene.layers.forEachIndexed { i, path ->
                val depth = i.toFloat() / (scene.layers.size - 1).coerceAtLeast(1)
                // Parallax в Connecting: дальний ~3px, передний ~10px.
                val dx = if (p > 0f) sin(p * 2.0 * Math.PI).toFloat() * (3f + 7f * depth) else 0f
                val color = lerpColor(palette.layers[i], palette.activeTint, a * (0.20f + 0.18f * depth))
                translate(dx, 0f) { drawPath(path, color) }
                // Туман у подножия дальнего слоя — разделение планов; в
                // Connecting туманы дышат в противофазе с parallax.
                if (i < scene.bases.lastIndex) {
                    val b = scene.bases[i]
                    val fogDx = if (p > 0f) sin(p * 2.0 * Math.PI + i).toFloat() * 10f else 0f
                    val bandTop = b - h * 0.05f
                    translate(fogDx, 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to palette.fog.copy(alpha = 0.65f),
                                1f to Color.Transparent,
                                startY = bandTop,
                                endY = bandTop + h * 0.14f,
                            ),
                            topLeft = Offset(-w * 0.05f, bandTop),
                            size = Size(w * 1.1f, h * 0.14f),
                        )
                    }
                }
            }

            // Широкая полоса тумана, плывущая между слоями во время подключения.
            if (p > 0f && p < 1f) {
                val top = h * 0.55f
                val zone = h - top
                val bandW = w * 0.7f
                val cx = lerp(-bandW, w, p)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to palette.fog.copy(alpha = 0.85f),
                        1f to Color.Transparent,
                        startX = cx,
                        endX = cx + bandW,
                    ),
                    topLeft = Offset(0f, top + zone * 0.22f),
                    size = Size(w, zone * 0.55f),
                )
            }
        },
    )
}

/** Системный «Reduce motion» (аниматоры выключены) — убираем ambient-движение. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** Вершина: позиция и высота в долях ширины/амплитуды слоя, полуширина склона. */
private data class Peak(val pos: Float, val height: Float, val width: Float)

/**
 * Рукотворные силуэты (ширина экрана = 1): каждый слой — массив из 2-3 гор
 * с широкими долинами. Слои упорядочены от дальнего к переднему.
 */
private val PEAKS = listOf(
    listOf(Peak(0.16f, 1.00f, 0.17f), Peak(0.52f, 0.82f, 0.21f), Peak(0.88f, 0.95f, 0.15f)),
    listOf(Peak(0.32f, 0.95f, 0.19f), Peak(0.74f, 1.00f, 0.22f)),
    listOf(Peak(0.08f, 0.75f, 0.15f), Peak(0.44f, 1.00f, 0.19f), Peak(0.82f, 0.70f, 0.17f)),
    listOf(Peak(0.22f, 0.60f, 0.17f), Peak(0.62f, 0.95f, 0.21f), Peak(0.96f, 0.65f, 0.13f)),
    listOf(Peak(0.38f, 0.90f, 0.25f), Peak(0.88f, 0.65f, 0.20f)),
)

/** Силуэты хребтов, пересчитываются только при смене размера. */
private class MountainPaths {
    data class Scene(val layers: List<Path>, val bases: List<Float>)

    private var w = 0f
    private var h = 0f
    private var scene: Scene = Scene(emptyList(), emptyList())

    fun get(width: Float, height: Float, count: Int): Scene {
        if (w == width && h == height && scene.layers.size == count) return scene
        w = width
        h = height
        val top = h * 0.55f
        val zone = h - top
        val pi = Math.PI.toFloat()

        val paths = (0 until count).map { i ->
            val depth = if (count <= 1) 1f else i.toFloat() / (count - 1)
            val baseY = top + zone * lerp(0.58f, 1.0f, depth)
            val amp = zone * (0.50f - 0.24f * depth)
            val peaks = PEAKS[i.coerceAtMost(PEAKS.lastIndex)]
            val s = i * 1.7f
            fun y(x: Float): Float {
                val t = x / w
                var m = 0f
                for (pk in peaks) {
                    val d = abs(t - pk.pos) / pk.width
                    if (d < 1f) m += (1f - d).pow(1.2f) * pk.height
                }
                // Спокойная базовая гряда между вершинами — широкие долины.
                m += 0.06f + 0.04f * sin(t * pi * 2f + s)
                return baseY - amp * m.coerceAtMost(1f)
            }
            Path().apply {
                moveTo(0f, h)
                lineTo(0f, y(0f))
                val steps = 120
                for (k in 1..steps) {
                    val x = w * k / steps
                    lineTo(x, y(x))
                }
                lineTo(w, h)
                close()
            }
        }
        val bases = (0 until count).map { i ->
            val depth = if (count <= 1) 1f else i.toFloat() / (count - 1)
            top + zone * lerp(0.58f, 1.0f, depth)
        }
        scene = Scene(paths, bases)
        return scene
    }
}
