package com.woka.ui.viewer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.woka.ui.viewer.player.SubtitleImage
import kotlin.math.roundToInt

/**
 * Draws a bitmap subtitle cue (PGS / VobSub, Step 10) where it was authored to
 * sit, unlike text — which the overlay places itself at the foot of the panel.
 *
 * It is composed **inside the frame's `graphicsLayer`** ([MediaViewerFrame]), so
 * the §1 zoom and pan apply to it for free with no duplicated transform maths, and
 * zooming past the frame crops the subtitle with the picture exactly as it does in
 * a hardware player.
 *
 * The subtitle canvas is mapped onto the drawn video rect axis by axis: uniform
 * whenever the two share an aspect ratio — every ordinary case, including a
 * 1920×1080 PGS canvas over 4K video — and a slight stretch rather than a
 * misplacement when they do not.
 */
@Composable
fun SubtitleBitmapLayer(
    image: SubtitleImage,
    frameSize: IntSize,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    if (image.rects.isEmpty() || frameSize.width <= 0 || frameSize.height <= 0) return
    val animPadding by animateDpAsState(targetValue = bottomPadding, animationSpec = tween(250))
    Canvas(modifier) {
        // The frame is drawn with ContentScale.Fit, so it is letterboxed inside the
        // panel; the subtitle belongs to that inner rect, not to the panel.
        val fit = minOf(size.width / frameSize.width, size.height / frameSize.height)
        val drawnWidth = frameSize.width * fit
        val drawnHeight = frameSize.height * fit
        val left = (size.width - drawnWidth) / 2f
        val top = (size.height - drawnHeight) / 2f
        val scaleX = drawnWidth / (image.canvasWidth.takeIf { it > 0 } ?: frameSize.width)
        val scaleY = drawnHeight / (image.canvasHeight.takeIf { it > 0 } ?: frameSize.height)

        // Cues are authored against the very bottom of the frame — the test fixture's
        // sit at y 935…998 of 1080 — which is exactly where the floating control bar
        // is. Lift them by the overlap alone, using the clearance the text overlay
        // already uses, and never far enough to push the top of a cue off the panel.
        val highest = image.rects.minOf { top + it.y * scaleY }
        val lowest = image.rects.maxOf { top + (it.y + it.bitmap.height) * scaleY }
        val lift = (lowest - (size.height - animPadding.toPx()))
            .coerceIn(0f, highest.coerceAtLeast(0f))

        image.rects.forEach { rect ->
            drawImage(
                image = rect.bitmap,
                dstOffset = IntOffset(
                    (left + rect.x * scaleX).roundToInt(),
                    (top + rect.y * scaleY - lift).roundToInt()
                ),
                dstSize = IntSize(
                    (rect.bitmap.width * scaleX).roundToInt(),
                    (rect.bitmap.height * scaleY).roundToInt()
                ),
                filterQuality = FilterQuality.Medium
            )
        }
    }
}
