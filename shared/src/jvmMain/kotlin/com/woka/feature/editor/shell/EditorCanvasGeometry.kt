package com.woka.feature.editor.shell

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/** Ten source pixels per screen pixel — 1000 % of native, as in the viewer. */
private const val MAX_SCALE = 10f

/**
 * Where the picture sits inside the canvas, and the two matrices that follow from it.
 *
 * **[canvasToSource] is the inverse of [sourceToCanvas], computed rather than
 * written.** Two independently derived matrices are two things that can disagree, and
 * the symptom — markup landing a few pixels from the cursor, worse the further from
 * centre — is the kind that gets blamed on the gesture code for a week.
 *
 * Both are fields rather than getters: a getter would mint a new `Matrix` on every
 * read, and these are read from a draw path.
 *
 * `PanBounds` and `ScaleMath` in the viewer are **not** reused. They are written
 * against `SizeMode` — a viewer concept with `Fit`, `Fill` and `Actual` — while the
 * editor has one mode and a magnifier on top of it. Sharing would mean widening their
 * model to carry a case the viewer does not have.
 */
@Immutable
internal class EditorCanvasGeometry(
    val sourceSize: IntSize,
    val canvasSize: IntSize,
    /** 1.0 is Fit. Above that magnifies, up to [maxZoom]. */
    val zoom: Float = 1f,
    /** Where the user dragged it, before clamping. */
    val pan: Offset = Offset.Zero
) {

    /** The whole picture visible, aspect kept — enlarging a small one to fill. */
    val fitScale: Float = if (
        sourceSize.width <= 0 || sourceSize.height <= 0 ||
        canvasSize.width <= 0 || canvasSize.height <= 0
    ) {
        // Before the first measurement. Drawing at 1.0 for one frame is invisible;
        // dividing by zero is not.
        1f
    } else {
        min(
            canvasSize.width.toFloat() / sourceSize.width,
            canvasSize.height.toFloat() / sourceSize.height
        )
    }

    /**
     * As far in as 1000 % of native, which is what the viewer offers and therefore what
     * the editor offers (2026-08-18). It was capped at 100 % on the argument that the
     * ≤2560 px proxy goes soft above that — true, and ME-116 is what sharpens it, but a
     * magnifier that will not magnify is the worse of the two failures.
     *
     * Never below 1.0: Fit is the floor, and a picture already drawn past 1000 % to fit
     * the canvas has nothing left to magnify into.
     */
    val maxZoom: Float = max(1f, MAX_SCALE / fitScale)

    val scale: Float = fitScale * zoom.coerceIn(1f, maxZoom)

    /** Half the amount the picture overflows the canvas, per axis, or zero. */
    private val slack: Offset = Offset(
        x = max(0f, (sourceSize.width * scale - canvasSize.width) / 2f),
        y = max(0f, (sourceSize.height * scale - canvasSize.height) / 2f)
    )

    /**
     * The pan actually applied — clamped so an edge can be brought to the middle of
     * the canvas but the picture can never be dragged out of view. On an axis with no
     * overflow the clamp is to zero, which is what keeps a Fit picture centred no
     * matter how hard it is dragged.
     */
    val clampedPan: Offset = Offset(
        x = pan.x.coerceIn(-slack.x, slack.x),
        y = pan.y.coerceIn(-slack.y, slack.y)
    )

    /** Top-left of the drawn picture: centred, then moved by the clamped pan. */
    val offset: Offset = Offset(
        x = (canvasSize.width - sourceSize.width * scale) / 2f + clampedPan.x,
        y = (canvasSize.height - sourceSize.height * scale) / 2f + clampedPan.y
    )

    val sourceToCanvas: Matrix = Matrix().apply {
        translate(offset.x, offset.y)
        scale(scale, scale)
    }

    val canvasToSource: Matrix = Matrix().apply {
        setFrom(sourceToCanvas)
        invert()
    }

    /**
     * The pan that keeps the source point under [focus] under it after zooming.
     *
     * Without this the picture magnifies about its centre, so the detail the user
     * pointed at slides away from the cursor — which reads as the zoom being drunk
     * rather than as it being centred somewhere else.
     */
    fun panForZoomAbout(focus: Offset, nextZoom: Float): Offset {
        val source = canvasToSource.map(focus)
        val nextScale = fitScale * nextZoom.coerceIn(1f, maxZoom)
        val centredX = (canvasSize.width - sourceSize.width * nextScale) / 2f
        val centredY = (canvasSize.height - sourceSize.height * nextScale) / 2f
        return Offset(
            x = focus.x - centredX - source.x * nextScale,
            y = focus.y - centredY - source.y * nextScale
        )
    }
}
