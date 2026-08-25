package dev.triplet.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp as lerpColor
import dev.triplet.app.vpn.VpnState

data class MountainScene(val hue: Color, val alphas: List<Float>, val fog: Color, val activeTint: Color)

val AppTheme.mountains: MountainScene
    get() = MountainScene(colors.mountainHue, colors.mountainAlphas, colors.fog, colors.activeMountainTint)

@Composable
fun DetourMountainBackground(scene: MountainScene, state: VpnState, modifier: Modifier = Modifier) {
    val active = remember { Animatable(0f) }
    LaunchedEffect(state) {
        active.animateTo(if (state == VpnState.Active) 1f else 0f, tween(220, easing = FastOutSlowInEasing))
    }
    Spacer(modifier.drawBehind {
        val a = active.value
        val color = lerpColor(scene.hue, scene.activeTint, a * .08f)
        val paths = listOf(
            farBackMountains(size.width, size.height),
            farMountains(size.width, size.height),
            middleMountains(size.width, size.height),
            nearMountains(size.width, size.height),
            foregroundMountains(size.width, size.height),
        )
        paths.forEachIndexed { index, path ->
            val alpha = scene.alphas.getOrElse(index) { .12f } * (1f + a * .08f)
            drawPath(path, color.copy(alpha = alpha))
            if (index < 3) {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .5f to scene.fog.copy(alpha = .10f - index * .015f),
                        1f to Color.Transparent,
                        startY = size.height * (.58f + index * .08f),
                        endY = size.height * (.70f + index * .08f),
                    ),
                )
            }
        }
    })
}

private fun mountainPath(w: Float, h: Float, points: FloatArray): Path = Path().apply {
    moveTo(points[0] * w, points[1] * h)
    var i = 2
    while (i < points.size) {
        lineTo(points[i] * w, points[i + 1] * h)
        i += 2
    }
    lineTo(w, h)
    lineTo(0f, h)
    close()
}

// Five individually drawn ridges; points are intentionally angular and asymmetric.
private fun farBackMountains(w: Float, h: Float) = mountainPath(w, h, floatArrayOf(
    0f, .64f, .12f, .61f, .22f, .63f, .31f, .57f, .38f, .59f, .46f, .49f,
    .51f, .53f, .58f, .56f, .66f, .46f, .72f, .51f, .80f, .55f, .88f, .49f,
    1f, .55f,
))

private fun farMountains(w: Float, h: Float) = mountainPath(w, h, floatArrayOf(
    0f, .70f, .10f, .66f, .19f, .68f, .27f, .62f, .35f, .64f, .43f, .52f,
    .48f, .57f, .55f, .61f, .63f, .50f, .69f, .55f, .78f, .59f, .87f, .52f,
    .94f, .59f, 1f, .58f,
))

private fun middleMountains(w: Float, h: Float) = mountainPath(w, h, floatArrayOf(
    0f, .78f, .11f, .73f, .18f, .75f, .26f, .67f, .34f, .70f, .41f, .57f,
    .46f, .49f, .50f, .45f, .55f, .58f, .63f, .64f, .70f, .60f, .77f, .67f,
    .84f, .56f, .90f, .63f, 1f, .68f,
))

private fun nearMountains(w: Float, h: Float) = mountainPath(w, h, floatArrayOf(
    0f, .86f, .10f, .81f, .18f, .84f, .27f, .75f, .34f, .78f, .42f, .65f,
    .48f, .69f, .54f, .76f, .63f, .72f, .70f, .80f, .78f, .70f, .86f, .74f,
    .93f, .66f, 1f, .76f,
))

private fun foregroundMountains(w: Float, h: Float) = mountainPath(w, h, floatArrayOf(
    0f, .93f, .13f, .88f, .22f, .91f, .31f, .82f, .38f, .86f, .46f, .76f,
    .53f, .80f, .61f, .74f, .68f, .84f, .76f, .79f, .84f, .88f, .91f, .80f,
    1f, .85f,
))
