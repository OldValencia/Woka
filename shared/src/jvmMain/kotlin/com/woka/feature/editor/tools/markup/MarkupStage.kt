package com.woka.feature.editor.tools.markup

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.TimeWindow
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.engine.image.RasterStage

/**
 * **One stage for every shape**, drawn in list order — `MarkupTool.stage`, moved here when
 * ME-263 pushed the tool past the size mandate.
 *
 * One rather than one each, because the fold collapses paints into a list it walks per frame:
 * a hundred arrows would otherwise be a hundred stages, sorted and mapped, to produce the same
 * hundred draw calls.
 *
 * Null for an empty list — the rule every tool follows, so opening the panel and closing it
 * again leaves the document exactly as it was found.
 */
internal fun markupStage(params: ParamSet): ToolStage? {
    // A text shape nobody typed into draws nothing, so a list holding only those is a list of
    // nothing — and a stage that paints no pixel is still a stage the fold walks and sorts on
    // every frame.
    val shapes = params[MarkupTool.Shapes].filterNot { it is MarkupShape.Text && it.text.isBlank() }
    if (shapes.isEmpty()) return null
    val windows = params[MarkupWindowsParam]
    return RasterStage.Paint { atMs -> shapes.forEach { shape -> drawTimed(shape, windows[shape.id], atMs) } }
}

/** Past every picture's edge: Skia intersects a layer's bounds with the clip anyway. */
private val EVERYWHERE = Rect(-1e6f, -1e6f, 1e6f, 1e6f)

/**
 * [shape] at the envelope its [window] gives the timeline millisecond [atMs] (ME-263) — whole
 * with no window or no time, which is every still. A shape faded part way is drawn through a
 * layer, so its overlapping strokes fade as one mark rather than darkening where they cross.
 */
private fun DrawScope.drawTimed(shape: MarkupShape, window: TimeWindow?, atMs: Long?) {
    val alpha = if (window == null || atMs == null) 1f else window.envelopeAt(atMs)
    if (alpha <= 0f) return
    if (alpha >= 1f) return drawMarkup(shape)
    drawIntoCanvas { canvas ->
        canvas.saveLayer(EVERYWHERE, Paint().apply { this.alpha = alpha })
        drawMarkup(shape)
        canvas.restore()
    }
}
