package com.woka.feature.editor.tools.markup

import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec

/**
 * What choosing a pen does to the shape already selected (ME-114), split out of
 * `MarkupPen.kt` when the plate row arrived (ME-146) — the controls on that side, the
 * rule that ties them to the selection on this one.
 */

/**
 * Choosing a colour, a size or a plate **with a shape selected restyles that shape** as
 * well as setting the pen (asked for 2026-08-24).
 *
 * Both, deliberately, and in one operation: the swatch has to show what is selected *and*
 * be what the next shape is drawn with, or the row would mean two different things
 * depending on whether anything happened to be selected.
 *
 * Only the rows that **mean something for the shape selected**: `Width` for the four
 * stroked ones, `Text size` for a caption (ME-122), and `Fill` and `Corner` for a caption
 * alone (ME-146). The first two write the same field — a shape carries one number and a
 * `Text` calls it a font size — so the routing is which spec is allowed to reach it, not
 * which field it lands in.
 *
 * `Kind` is not among them: a drawn rectangle cannot become an arrow, because the two are
 * different classes carrying different geometry, and "convert this shape" is a feature
 * rather than a restyle. `Shapes` is excluded for the obvious reason — it *is* the list,
 * and rewriting it here would recurse.
 *
 * Null for "nothing to apply", including a selection naming a shape the document no longer
 * holds — an id outlives the undo that took its shape away — and including a plate chosen
 * with an arrow selected, which must cost no history entry at all.
 */
internal fun restyleSelected(
    spec: ParamSpec<*>,
    params: ParamSet,
    selectedId: String?
): ParamSet? {
    if (selectedId == null) return null

    val shapes = params[MarkupTool.Shapes]
    val selected = shapes.firstOrNull { it.id == selectedId } ?: return null
    val sizeParam = sizeParamFor(selected)
    val restyled = when (spec) {
        MarkupColorParam -> selected.styled(color = params[MarkupColorParam])
        sizeParam -> selected.styled(width = params[sizeParam])
        MarkupFillParam -> selected.takeIf { it is MarkupShape.Text }
            ?.filled(params[MarkupFillParam])
            ?: return null
        MarkupCornerParam -> selected.takeIf { it is MarkupShape.Text }
            ?.cornered(params[MarkupCornerParam])
            ?: return null
        else -> return null
    }

    return params.with(
        MarkupTool.Shapes,
        shapes.map { shape -> if (shape.id == selectedId) restyled else shape }
    )
}

/** Which of the two size rows this shape's `width` is spelled by. */
private fun sizeParamFor(shape: MarkupShape): FloatParam =
    if (shape is MarkupShape.Text) MarkupTextSizeParam else MarkupWidthParam
