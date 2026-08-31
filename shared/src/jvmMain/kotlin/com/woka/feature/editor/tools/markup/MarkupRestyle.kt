package com.woka.feature.editor.tools.markup

import androidx.compose.ui.geometry.Offset
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
 * stroked ones, `Text size` for a caption (ME-122), and `Fill`, `Corner` (ME-146) and
 * `Style` (ME-151) for a caption alone. The first two write the same field — a shape
 * carries one number and a `Text` calls it a font size — so the routing is which spec is
 * allowed to reach it, not which field it lands in.
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
    // **By key, never by identity.** A size row arrives here re-defaulted for the picture
    // (ME-159) and is therefore a *copy* of the declared spec, equal to it in key, range
    // and encoding and equal to nothing by `==`. Matching on the object sent every size
    // write down the `else` branch, so the row moved the pen and left the selected shape
    // where it was — which is the exact bug `MarkupSizeRowUiTest` was written for.
    val restyled = when (spec.key) {
        MarkupColorParam.key -> selected.styled(color = params[MarkupColorParam])
        sizeParam.key -> selected.resized(params[sizeParam])
        MarkupFillParam.key -> selected.takeIf { it is MarkupShape.Text }
            ?.filled(params[MarkupFillParam])
            ?: return null
        MarkupCornerParam.key -> selected.takeIf { it is MarkupShape.Text }
            ?.cornered(params[MarkupCornerParam])
            ?: return null
        MarkupFacesParam.key -> selected.takeIf { it is MarkupShape.Text }
            ?.faced(params[MarkupFacesParam])
            ?: return null
        else -> return null
    }

    return params.with(
        MarkupTool.Shapes,
        shapes.map { shape -> if (shape.id == selectedId) restyled else shape }
    )
}

/**
 * The pen, overridden by whatever is selected — what the panel reports (reported by hand
 * 2026-08-29).
 *
 * **Exactly the specs [restyleSelected] can write, and no others.** A control that shows
 * one value and writes another is worse than one that shows the pen: `Faces` sends the
 * whole set it believes is lit, so a caption whose `Bold` had been cleared elsewhere would
 * have had it turned back *on* by a click meant to clear it.
 *
 * `Kind` is deliberately not among them. It is the chooser the rows are keyed on
 * (`markupRows`), so following the selection would make the panel change shape under a
 * click on the picture — the thing that rule exists to prevent.
 *
 * Nothing is written. An id naming a shape the document no longer holds — an id outlives
 * the undo that took its shape away — falls back to the pen, like no selection at all.
 */
internal fun displaySelected(params: ParamSet, selectedId: String?): ParamSet {
    if (selectedId == null) return params
    val selected = params[MarkupTool.Shapes].firstOrNull { it.id == selectedId } ?: return params

    val pen = params
        .with(MarkupColorParam, selected.color)
        .with(sizeParamFor(selected), selected.width)
    return if (selected !is MarkupShape.Text) {
        pen
    } else {
        pen
            .with(MarkupFillParam, selected.fill)
            .with(MarkupCornerParam, selected.corner)
            .with(MarkupFacesParam, selected.faces)
    }
}

/** Which of the two size rows this shape's `width` is spelled by. */
private fun sizeParamFor(shape: MarkupShape): FloatParam =
    if (shape is MarkupShape.Text) MarkupTextSizeParam else MarkupWidthParam

/**
 * The shape at [size] — and for a caption that is a **scale**, not a written field
 * (ME-148).
 *
 * The row and a corner drag are then one operation reached two ways. They were not before:
 * the slider wrote `width` and left `wrap` where it was, so moving it reflowed a caption
 * that a corner drag would have kept intact — two controls over one thing, disagreeing
 * about what a caption's proportions are.
 *
 * The anchor is the caption's own `at`, because a slider has no corner to name: it grows
 * down and to the right from where the caption sits. `scaled` is what clamps the result,
 * so the row cannot drive `wrap` past a size the type has stopped reaching.
 *
 * A stroke has no box to keep in proportion, so the four keep writing the field directly.
 */
private fun MarkupShape.resized(size: Float): MarkupShape = when (this) {
    is MarkupShape.Text -> scaled(size / width, Offset(at.x, at.y))
    else -> styled(width = size)
}
