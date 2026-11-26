package com.woka.feature.editor.tools.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.BoolValue
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolOp
import kotlin.math.roundToInt

/**
 * Reading a crop out of a document and writing one back (ME-109).
 *
 * Split from the overlay so that "what the document currently says" is answered in one
 * place: the pending operation wins over the committed one, because a drag in flight is
 * what the user is looking at, and `Single` cardinality means there is never more than
 * one committed crop to choose between.
 */

/** The crop in flight, then the committed one, then nothing. */
fun EditDocument.cropOp(): ToolOp? =
    pending?.takeIf { it.toolId == CropTool.manifest.id }
        ?: opsOf(CropTool.manifest.id).lastOrNull()

/**
 * The rect to draw for [frame] — the stored one, or the whole picture when nothing has
 * been chosen yet.
 *
 * **An unset rect is not written to the document** (`CropTool.stage`), so the overlay
 * shows the frame and composes nothing until the first drag. That is what makes opening
 * the tool and closing it again leave the document untouched.
 */
fun EditDocument.cropRectIn(frame: IntSize): CropRect {
    val params = cropOp()?.params ?: return CropRect.of(frame)
    val stored = params[CropTool.Rect]
    if (stored.width <= 0 || stored.height <= 0) return CropRect.of(frame)
    return stored.landedIn(params[CropTool.Frame], frame).clampedTo(frame)
}

/**
 * The rect, chosen in [chosen], where it falls in [frame] (RD-205) — **the one function the
 * overlay and the fold both ask**, so the rectangle drawn and the rectangle cut are the same
 * integers. A crop with no recorded frame (saved before RD-205) is the rect as stored,
 * unclamped, which is exactly what `CropTool.stage` folded before.
 */
internal fun CropRect.landedIn(chosen: CropFrame, frame: IntSize): CropRect {
    val from = chosen.toIntSizeOrNull() ?: return this
    return scaledTo(from, frame).clampedTo(frame)
}

/** The aspect the stored crop is locked to, or [CropAspect.Free]. */
fun EditDocument.cropAspect(): CropAspect =
    cropOp()?.params?.get(CropTool.Aspect) ?: CropAspect.Free

/**
 * Whether the stored crop is turned on its side.
 *
 * **The `BoolValue` branch is a migration, not a fallback** (ME-144). `Portrait` was a
 * `BoolParam` until it needed a glyph per option; the key on the wire did not change, so
 * a crop saved before that still holds a boolean under it. Without this every ratio a
 * user had turned would quietly come back upright — `ChoiceParam.decode` answers null on
 * a type mismatch and `ParamSet` substitutes the default, which is exactly the silent
 * loss §3.4 promises not to have.
 */
fun EditDocument.cropIsPortrait(): Boolean {
    val params = cropOp()?.params ?: return false
    (params.values[CropTool.Portrait.key] as? BoolValue)?.let { return it.value }
    return params[CropTool.Portrait] == CropOrientation.Portrait
}

/**
 * The ratio the rectangle is actually held to: the chosen aspect, on its side when
 * `Portrait` is on. Null is `Free`, which locks nothing.
 */
fun EditDocument.cropRatioIn(frame: IntSize): Float? {
    val aspect = cropAspect()
    return if (cropIsPortrait()) aspect.flippedIn(frame) else aspect.ratioIn(frame)
}

/**
 * The operation that records [rect], carrying every other parameter the document
 * already held — a drag must not silently reset the chosen ratio. [frame] is the frame the
 * rect is drawn in, recorded beside it so a later straighten can scale it (RD-205).
 */
fun EditDocument.cropOpWith(rect: CropRect, frame: IntSize): ToolOp {
    // From the tool's defaults when there is nothing yet, never from an empty set: a
    // partial `ParamSet` is one the inspector completes on the next composition, and
    // that write is an undo step nobody asked for.
    val params = cropOp()?.params ?: CropTool.defaults()
    return ToolOp(
        toolId = CropTool.manifest.id,
        // Only the rect: the ratio and the orientation are the inspector's to write,
        // and a drag that carried them along would undo a chip the user had just set.
        params = params.with(CropTool.Rect, rect).with(CropTool.Frame, CropFrame.of(frame))
    )
}

/**
 * A canvas position as **source** pixels, rounded.
 *
 * Rounded here and nowhere else: a rect is whole pixels (`CropRect`), and rounding at
 * each of the four edges separately is how a drag ends up a pixel wider than the
 * distance the pointer actually travelled.
 */
fun Matrix.toSource(position: Offset): Pair<Int, Int> {
    val mapped = map(position)
    return mapped.x.roundToInt() to mapped.y.roundToInt()
}
