package com.woka.feature.editor.engine.video

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.tools.trim.TrimClip
import kotlin.math.ceil

/** The mockup's `rgba(0,0,0,0.35)` hairline between two stills. */
private val CELL_SEAM = Color.Black.copy(alpha = 0.35f)

/** Before a still has landed: a faint tint over the clip's well, so an empty clip reads as one. */
private val UNLOADED = Color.White.copy(alpha = 0.08f)

/*
 * A clip's stills, drawn — split out of `ClipTile` when its wave strip and name (RD-301) took
 * it past the size mandate. The skim badge crops its still with the same [centredCrop].
 */

/**
 * Stills at the strip's own height and the frame's 16:9, each cropped to its slot rather
 * than squeezed, and each showing the source instant under its own middle.
 */
internal fun DrawScope.drawStills(clip: TrimClip, cells: FilmstripCells?, phase: Float, fullWidth: Float, shift: Float): Boolean {
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
internal fun centredCrop(width: Int, height: Int, aspect: Float): Pair<IntOffset, IntSize> {
    if (height <= 0 || width <= 0) return IntOffset.Zero to IntSize(width, height)
    return if (width.toFloat() / height > aspect) {
        val cropWidth = (height * aspect).toInt().coerceIn(1, width)
        IntOffset((width - cropWidth) / 2, 0) to IntSize(cropWidth, height)
    } else {
        val cropHeight = (width / aspect).toInt().coerceIn(1, height)
        IntOffset(0, (height - cropHeight) / 2) to IntSize(width, cropHeight)
    }
}
