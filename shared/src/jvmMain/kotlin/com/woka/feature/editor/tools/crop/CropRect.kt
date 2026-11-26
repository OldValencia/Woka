package com.woka.feature.editor.tools.crop

import androidx.compose.ui.unit.IntSize
import kotlinx.serialization.Serializable

/**
 * The rectangle a crop keeps, in **source** pixels (ME-109).
 *
 * Source, never canvas and never proxy: the same rect has to mean the same region when
 * the editor draws it at 40 % of a 2560 px proxy, when the grid folds it into a 400 px
 * cover and when the save runs it against the native bitmap. `RasterRecipe.scaledBy` is
 * what moves it between those, and it can only do that because the stored numbers are
 * of one fixed thing.
 *
 * `Int`, not `Float`: a crop names whole pixels, and a rect that has drifted a third of
 * a pixel through a dozen drags is a file whose dimensions depend on how it was reached.
 *
 * Serialised into an `ObjectParam`, so this **is** the wire format — a field renamed
 * here rewrites what every saved document means. `editorJson` is `ignoreUnknownKeys`,
 * so adding one is safe and removing one is not.
 */
@Serializable
data class CropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height

    val size: IntSize get() = IntSize(width, height)

    /** Whether this keeps the whole of [frame] — the crop that is not a crop. */
    fun covers(frame: IntSize): Boolean =
        left <= 0 && top <= 0 && width >= frame.width && height >= frame.height

    companion object {
        /**
         * The rect a tool opens on: the whole frame, which composes no stage at all
         * until the user moves a handle.
         */
        fun of(frame: IntSize) = CropRect(0, 0, frame.width, frame.height)

        /** What an `ObjectParam` needs before it has ever seen a frame. */
        val Unset = CropRect(0, 0, 0, 0)
    }
}

/**
 * The frame a crop was chosen in (RD-205) — the drawn picture's size when the rect was last
 * written. A straighten changes that size with every angle, and a rect in pixels of the old
 * frame would sit somewhere else in the new one; with this the rect is scaled to the frame it
 * lands on (`CropRect.landedIn`) and stays where it was drawn.
 *
 * [Unset] in every crop saved before RD-205, which then folds exactly as it always did. Wire
 * format, as [CropRect] is.
 */
@Serializable
data class CropFrame(val width: Int, val height: Int) {

    /** Null while unset: no frame was recorded, so nothing is scaled. */
    fun toIntSizeOrNull(): IntSize? =
        if (width > 0 && height > 0) IntSize(width, height) else null

    companion object {
        val Unset = CropFrame(0, 0)

        fun of(frame: IntSize) = CropFrame(frame.width, frame.height)
    }
}
