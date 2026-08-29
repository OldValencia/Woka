package com.woka.feature.editor.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * The inspector and whatever it has opened beside it, drawn as **one plate that stretches
 * apart** (asked for 2026-08-29, against the `gooey examples` the request named).
 *
 * The effect those examples use is two steps and no more: blur the shapes, then push the
 * blurred alpha back through a steep ramp (`gooEffect`). The panel starts life *inside* the
 * card's own footprint, so the two silhouettes are one blob; as it travels out, the blur
 * keeps them joined by a neck until they are too far apart for it, and the neck snaps. That
 * is the whole animation — nothing is drawn to make it, the shapes move and the filter
 * follows.
 *
 * **The plates are filtered, never the content.** A filter over the whole panel would blur
 * the swatches and the type; the CSS examples draw their blobs on a background layer for
 * exactly this reason, and this does the same — the two cards paint nothing of their own
 * while the pair is open, and their controls are drawn on top untouched.
 */
@Composable
internal fun GooeyPair(
    /** 0 while closed, 1 while fully out — a spring, so the plate overshoots and settles. */
    progress: Float,
    extra: (@Composable () -> Unit)?,
    main: @Composable (plateAlpha: Float) -> Unit
) {
    var mainPlate by remember { mutableStateOf(PlateBounds()) }
    var extraPlate by remember { mutableStateOf(PlateBounds()) }
    val density = LocalDensity.current
    val scrim = Theme.viewer.popoverScrim
    val radius = with(density) { Theme.viewer.cornerRadius.toPx() }
    val blur = with(density) { GOO_BLUR.toPx() }
    val gap = with(density) { PAIR_GAP.toPx() }

    Box(contentAlignment = Alignment.BottomStart) {
        // Only once there is something to merge with: one plate has nothing to be gooey
        // about and would pay a blur per frame for it.
        if (progress > 0f) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // The scrim's translucency is applied to the *layer*, not to the
                        // shapes: the ramp saturates any partial alpha to solid, so a
                        // translucent fill would come out opaque and take the photograph
                        // underneath with it.
                        alpha = scrim.alpha * progress
                        renderEffect = gooEffect(blur)
                        // The blob is wider than the shapes that made it, and the neck is
                        // the part that would be cut off.
                        clip = false
                    }
            ) {
                val opaque = scrim.copy(alpha = 1f)
                drawPlate(opaque, mainPlate, radius)
                drawPlate(opaque, extraPlate, radius)
            }
        }

        PairLayout(
            progress = progress,
            gap = gap,
            main = {
                Box(modifier = Modifier.onPlaced { mainPlate = it.bounds() }) {
                    main(1f - progress)
                }
            },
            extra = {
                if (extra != null) {
                    Box(
                        modifier = Modifier
                            .onPlaced { extraPlate = it.bounds() }
                            // **Alpha only, never a translation.** The plate the blob is
                            // drawn from comes from `onPlaced`, and a `graphicsLayer`
                            // translation is a draw-time transform that placement never
                            // hears about — the blob would stay behind while the card
                            // moved. The travel is in the layout below.
                            .graphicsLayer {
                                alpha = ((progress - CONTENT_DELAY) / (1f - CONTENT_DELAY))
                                    .coerceIn(0f, 1f)
                            }
                    ) {
                        extra()
                    }
                }
            }
        )
    }
}


private fun DrawScope.drawPlate(color: Color, plate: PlateBounds, radius: Float) {
    if (plate.width <= 0f || plate.height <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(plate.x, plate.y),
        size = Size(plate.width, plate.height),
        cornerRadius = CornerRadius(radius, radius)
    )
}


/**
 * Air between the card and the panel **once it has arrived** (asked for 2026-08-29).
 *
 * At 10 the blur reached across it and the two never came apart — one blob with a waist,
 * which is not what "beside the card" looks like. At 18 they part: the neck belongs to the
 * *travel*, where the panel is still under the card and the gap has not opened yet.
 */
internal val PAIR_GAP = 18.dp

/** Wide enough to bridge the gap while it is closing, and not once it is open. */
private val GOO_BLUR = 8.dp

/** How much of the travel happens before the panel's own contents fade up. */
private const val CONTENT_DELAY = 0.45f
