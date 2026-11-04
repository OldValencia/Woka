package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.viewer.video.formatTime
import kotlin.math.ceil

/** `.clip`'s radius in `video-editor.html`. */
internal val CLIP_RADIUS = 5.dp

/** `.clip.sel`'s outline: the accent marks selection, as everywhere in the editor. */
private val SELECTED_OUTLINE = 2.dp

/** `.clip.dragging`: the carried clip rides 5 dp above its neighbours. */
private val LIFT = 5.dp

/** The mockup's `rgba(0,0,0,0.35)` hairline between two stills. */
private val CELL_SEAM = Color.Black.copy(alpha = 0.35f)

/** Before a still has landed: the scrim's own hover tint, so an empty strip reads as one. */
private val UNLOADED = Color.White.copy(alpha = 0.08f)

/**
 * One clip on the strip: its stretch of the filmstrip, its range when there is room for
 * words, and the two states the mockup gives it — selected, and carried (ME-211, ME-213).
 *
 * **The stills are drawn, not composed.** A tile per still would be a dozen composables
 * per clip recomposing as each one lands; drawn here, a still that arrives repaints this
 * clip and nothing else, because `stillAt` reads the snapshot map inside the draw scope.
 */
@Composable
internal fun ClipTile(
    clip: TrimClip,
    cells: FilmstripCells?,
    showRange: Boolean,
    selected: Boolean,
    carried: Boolean,
    /** The clip carries a look of its own — ME-157's dot, on a film's clip (ME-215). */
    ownLook: Boolean = false,
    modifier: Modifier = Modifier,
    /**
     * The whole clip's width when this tile draws only the part on screen, and how far into
     * it that part starts (ME-218) — so a zoomed tile shows the stills of its own stretch.
     */
    fullWidthPx: Float = 0f,
    shiftPx: Float = 0f
) {
    val shape = RoundedCornerShape(CLIP_RADIUS)
    Box(
        modifier = modifier
            .graphicsLayer { translationY = if (carried) -LIFT.toPx() else 0f }
            .clip(shape)
            .then(if (selected) Modifier.border(SELECTED_OUTLINE, Theme.colors.accentEditor, shape) else Modifier)
    ) {
        // A slot still decoding shimmers (2026-11-04); the draw says whether any is left.
        val loading = remember { mutableStateOf(true) }
        val phase = rememberShimmer(loading.value)
        Canvas(Modifier.fillMaxSize()) {
            val missing = drawStills(clip, cells, phase.value, fullWidthPx.takeIf { it > 0f } ?: size.width, shiftPx)
            if (missing != loading.value) loading.value = missing
        }
        if (showRange) {
            BasicText(
                text = "${formatTime(clip.fromMs)} – ${formatTime(clip.toMs)}",
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 3.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 9.5.sp)
            )
        }
        ClipBadges(ownLook = ownLook, speed = clip.speed, holdMs = clip.holdMs)
    }
}

/**
 * Stills at the strip's own height and the frame's 16:9, each cropped to its slot rather
 * than squeezed, and each showing the source instant under its own middle.
 */
private fun DrawScope.drawStills(clip: TrimClip, cells: FilmstripCells?, phase: Float, fullWidth: Float, shift: Float): Boolean {
    drawRect(UNLOADED)
    val tileHeight = size.height
    val tileWidth = tileHeight * 16f / 9f
    if (size.width <= 0f || tileWidth <= 0f || fullWidth <= 0f) return false
    var missing = false
    // Slots are placed on the whole clip and drawn shifted, so only the ones on screen run.
    val firstSlot = (shift / tileWidth).toInt()
    val lastSlot = ceil((shift + size.width) / tileWidth).toInt()
    clipRect {
        for (j in firstSlot until lastSlot) {
            val x = j * tileWidth - shift
            val atMs = clip.fromMs + (clip.sourceLengthMs * ((j * tileWidth + tileWidth / 2f) / fullWidth)).toLong()
            // The slot's own stretch of source, so a still already decoded inside it is used.
            val spanMs = (clip.sourceLengthMs * (tileWidth / fullWidth)).toLong()
            val still = cells?.stillAt(atMs.coerceAtMost(clip.toMs), spanMs)
            if (still != null) {
                val crop = centredCrop(still.width, still.height, tileWidth / tileHeight)
                drawImage(
                    image = still,
                    srcOffset = crop.first,
                    srcSize = crop.second,
                    dstOffset = IntOffset(x.toInt(), 0),
                    dstSize = IntSize(ceil(tileWidth).toInt(), tileHeight.toInt())
                )
            } else if (cells != null) {
                // Only while something is decoding it; with no decoder there is nothing to wait for.
                missing = true
                drawShimmer(x, Size(tileWidth, tileHeight), phase)
            }
            if (j > 0 && x >= 0f) drawRect(CELL_SEAM, topLeft = Offset(x, 0f), size = Size(1f, tileHeight))
        }
    }
    return missing
}

/** The centred part of a [width] × [height] still that has the slot's [aspect]. */
private fun centredCrop(width: Int, height: Int, aspect: Float): Pair<IntOffset, IntSize> {
    if (height <= 0 || width <= 0) return IntOffset.Zero to IntSize(width, height)
    return if (width.toFloat() / height > aspect) {
        val cropWidth = (height * aspect).toInt().coerceIn(1, width)
        IntOffset((width - cropWidth) / 2, 0) to IntSize(cropWidth, height)
    } else {
        val cropHeight = (width / aspect).toInt().coerceIn(1, height)
        IntOffset(0, (height - cropHeight) / 2) to IntSize(width, cropHeight)
    }
}
