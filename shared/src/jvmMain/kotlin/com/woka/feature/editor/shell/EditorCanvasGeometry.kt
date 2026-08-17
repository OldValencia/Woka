package com.woka.feature.editor.shell

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/**
 * Where the picture sits inside the canvas, and the two matrices that follow from it.
 *
 * `Fit` and nothing else in v1 (§9, 2026-08-18): above 100 % the canvas draws a
 * ≤2560 px proxy, so a zoom gesture without ME-116's native-region draw would ship
 * blurred. [scale] is therefore always the fit scale, never a user value — and it
 * *does* enlarge a picture smaller than the canvas, because an editor exists to show
 * you what you are changing.
 *
 * **[canvasToSource] is the inverse of [sourceToCanvas], computed rather than
 * written.** Two independently derived matrices are two things that can disagree, and
 * the symptom — markup landing a few pixels from the cursor, worse the further from
 * centre — is the kind that gets blamed on the gesture code for a week.
 *
 * Both are held as fields rather than computed in a getter: a getter would mint a new
 * `Matrix` on every read, and these are read from a draw path. `Matrix` is mutable, so
 * a caller that needs to transform in place must copy first — nothing here does.
 */
@Immutable
internal class EditorCanvasGeometry(
    val sourceSize: IntSize,
    val canvasSize: IntSize
) {

    /** Fit: the whole picture visible, aspect kept. */
    val scale: Float = if (
        sourceSize.width <= 0 || sourceSize.height <= 0 ||
        canvasSize.width <= 0 || canvasSize.height <= 0
    ) {
        // Before the first measurement, and for a source whose size is not known yet.
        // Drawing at 1.0 for one frame is invisible; dividing by zero is not.
        1f
    } else {
        min(
            canvasSize.width.toFloat() / sourceSize.width,
            canvasSize.height.toFloat() / sourceSize.height
        )
    }

    /** Top-left of the drawn picture, centred in the canvas. */
    val offset: Offset = Offset(
        x = (canvasSize.width - sourceSize.width * scale) / 2f,
        y = (canvasSize.height - sourceSize.height * scale) / 2f
    )

    val sourceToCanvas: Matrix = Matrix().apply {
        translate(offset.x, offset.y)
        scale(scale, scale)
    }

    val canvasToSource: Matrix = Matrix().apply {
        setFrom(sourceToCanvas)
        invert()
    }
}
