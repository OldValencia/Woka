package io.iris.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.random.Random

/**
 * No gradients anywhere in this file — deliberately. A gradient is a continuous
 * ramp that Skia rasterizes and quantizes to 8-bit per channel; on Windows, and
 * especially on a large/high-contrast (OLED) panel, that quantization shows up as
 * visible banding that neither dithering nor blur fully hid in practice (macOS
 * gets away with it because WindowServer dithers the *entire* screen output for
 * every app; Windows' DWM doesn't do that for ordinary SDR desktop composition).
 *
 * A flat fill has no ramp to quantize — the color is identical at every pixel, so
 * there's nothing to band, on any OS or GPU. Life comes from a slow, whole-field
 * color drift (one color value animating over time) rather than a spatial one,
 * plus a static grain texture for material depth instead of glossy flatness.
 */
private const val GrainTileSize = 64

/**
 * The grain bitmap is generated at *twice* [GrainTileSize] and drawn scaled down
 * to it, so the default bilinear downscale blends it into finer, softer speckles
 * than a 1:1 draw would.
 */
private const val GrainSourceSize = GrainTileSize * 2

/** Understated — this is texture, not a visual effect competing for attention. */
private const val GrainStrength = 0.035f

private fun generateGrainTile(size: Int): ImageBitmap {
    val bitmap = ImageBitmap(size, size)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val random = Random(0x6172_6973) // fixed seed: stable, reproducible grain
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(size.toFloat(), size.toFloat())
    ) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = random.nextInt(256)
                drawRect(
                    color = Color(v, v, v, 255),
                    topLeft = Offset(x.toFloat(), y.toFloat()),
                    size = Size(1f, 1f)
                )
            }
        }
    }
    return bitmap
}

private data class Mood(val a: Color, val b: Color)

// Two close neighbors per theme — the drift between them should be felt over the
// course of a minute, never seen happening.
private val DarkMood = Mood(a = Color(0xFF121219), b = Color(0xFF16161F))
private val LightMood = Mood(a = Color(0xFFF7F7FA), b = Color(0xFFF1F1F5))

@Composable
fun AmbientBackground(isDark: Boolean) {
    val grainTile = remember { generateGrainTile(GrainSourceSize) }
    val mood = if (isDark) DarkMood else LightMood

    val infiniteTransition = rememberInfiniteTransition()
    val t by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Reverse (not Restart) so there is no loop seam to worry about at all.
            animation = tween(durationMillis = 50_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val baseColor = lerp(mood.a, mood.b, t)

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = baseColor)

        val width = size.width
        val height = size.height
        var ty = 0f
        while (ty < height) {
            var tx = 0f
            while (tx < width) {
                drawImage(
                    image = grainTile,
                    srcSize = IntSize(GrainSourceSize, GrainSourceSize),
                    dstOffset = IntOffset(tx.toInt(), ty.toInt()),
                    dstSize = IntSize(GrainTileSize, GrainTileSize),
                    alpha = GrainStrength,
                    blendMode = BlendMode.Overlay
                )
                tx += GrainTileSize
            }
            ty += GrainTileSize
        }
    }
}
