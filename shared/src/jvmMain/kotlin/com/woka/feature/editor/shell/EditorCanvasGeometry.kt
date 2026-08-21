package com.woka.feature.editor.shell

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.viewer.shell.zoomBounds
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
 *
 * **`zoomBounds` is the exception, and it is reused on purpose.** It takes two sizes
 * and answers a range of percentages; it names no `SizeMode` and knows nothing about
 * presets. How far a picture may be zoomed is one rule in this app, not one per panel.
 */
@Immutable
internal class EditorCanvasGeometry(
    val sourceSize: IntSize,
    val canvasSize: IntSize,
    /** 1.0 is Fit. Above it magnifies to [maxZoom], below it shrinks to [minZoom]. */
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
     * Never below 1.0, so Fit is always reachable: a picture the canvas already draws
     * past 1000 % has nothing left to magnify into, and its ceiling *is* Fit.
     */
    val maxZoom: Float = max(1f, MAX_SCALE / fitScale)

    /**
     * **Fit is not the floor** (2026-08-21). It was, and that made the editor the one
     * panel where a picture could be made larger but never smaller — while the viewer,
     * one layer below on the same file, zooms out to a tenth of the fitted size.
     *
     * The number is the viewer's own `zoomBounds`, converted from percent-of-native
     * onto this scale, so neither panel can drift from the other. Never above 1.0: a
     * floor that sits past Fit would put the picture's own resting place out of reach.
     */
    val minZoom: Float = if (fitScale <= 0f) {
        1f
    } else {
        (zoomBounds(canvasSize, sourceSize).first / (fitScale * 100f)).coerceAtMost(1f)
    }

    val scale: Float = fitScale * zoom.coerceIn(minZoom, maxZoom)

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
        val nextScale = fitScale * nextZoom.coerceIn(minZoom, maxZoom)
        val centredX = (canvasSize.width - sourceSize.width * nextScale) / 2f
        val centredY = (canvasSize.height - sourceSize.height * nextScale) / 2f
        return Offset(
            x = focus.x - centredX - source.x * nextScale,
            y = focus.y - centredY - source.y * nextScale
        )
    }
}
