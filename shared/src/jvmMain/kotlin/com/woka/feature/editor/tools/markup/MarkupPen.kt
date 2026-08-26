package com.woka.feature.editor.tools.markup

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec

/**
 * The pen — what the *next* shape is drawn with — and what choosing it does to the shape
 * already selected (ME-114).
 *
 * Split from `MarkupTool` for the size mandate, at the seam that was already there: the
 * manifest and `stage()` on one side, and on this one the three controls plus the rule that
 * ties them to the selection.
 */

/**
 * The seven offered colours: the editor's own accent first, then the five the rest of the
 * app already speaks (`Theme.Colors`), then white for a dark picture.
 *
 * `Long` ARGB rather than `Color`, because a swatch is a document value before it is a
 * pixel (`ParamValue.ColorValue`).
 */
internal val MARKUP_SWATCHES = listOf(
    0xFFFF9F0AL,
    0xFFFF3B30L,
    0xFFFFD60AL,
    0xFF30D158L,
    0xFF0A84FFL,
    0xFF000000L,
    0xFFFFFFFFL
)

/**
 * The five shapes, **as glyphs** (asked for 2026-08-27).
 *
 * Five names is past `ChoiceParamRow`'s segmented limit, so this is a chip row — and
 * "Rectangle", "Freehand" and "Ellipse" spelled out wrapped it onto two lines, which
 * reflowed the whole inspector as the pen was changed. `labelOf` still names every one of
 * them; `ChipRow` moves that name into a tooltip rather than dropping it.
 */
internal val MarkupKindParam = ChoiceParam(
    key = "kind",
    label = "Shape",
    options = MarkupKind.entries.toList(),
    default = MarkupKind.Arrow,
    iconOf = { kind ->
        when (kind) {
            MarkupKind.Arrow -> Icons.Static.ShapeArrow
            MarkupKind.Rectangle -> Icons.Static.ShapeRectangle
            MarkupKind.Ellipse -> Icons.Static.ShapeEllipse
            MarkupKind.Freehand -> Icons.Static.ShapeFreehand
            MarkupKind.Text -> Icons.Static.ShapeText
        }
    }
)

internal val MarkupColorParam = ColorParam(
    key = "color",
    label = "Color",
    swatches = MARKUP_SWATCHES,
    // The editor accent, which is what the design log settled markup's colour on
    // (2026-08-18) and what every other editor control already uses.
    default = 0xFFFF9F0AL
)

/**
 * Stroke width in **source** pixels, so a mark keeps its size relative to the picture
 * rather than to the window it was drawn in. 6 px reads on a photograph and is thin enough
 * to point at something on a screenshot.
 */
internal val MarkupWidthParam =
    FloatParam("width", "Width", 1f..64f, default = 6f, unit = "px")

/**
 * The size of type, in **source** pixels — a separate control from the stroke width and
 * deliberately not a wider version of it (ME-122).
 *
 * A stroke reads at 6 px and 64 is already heavy; type at 64 px on a 6000 px photograph
 * is a caption nobody can read. One slider spanning both would leave the stroke four
 * pixels of travel between "thin" and "thick", so there are two, and each is a no-op
 * against the shapes the other one styles ([restyleSelected]).
 */
internal val MarkupTextSizeParam =
    FloatParam("textSize", "Text size", 12f..512f, default = 64f, unit = "px")

/**
 * Choosing a colour or a width **with a shape selected restyles that shape** as well as
 * setting the pen (asked for 2026-08-24).
 *
 * Both, deliberately, and in one operation: the swatch has to show what is selected *and*
 * be what the next shape is drawn with, or the row would mean two different things
 * depending on whether anything happened to be selected.
 *
 * Only the colour and **the size row that means something for the selected shape**:
 * `Width` for the four stroked ones, `Text size` for a text shape (ME-122). Both write the
 * same field — a shape carries one number and a `Text` calls it a font size — so the
 * routing is which spec is allowed to reach it, not which field it lands in.
 *
 * `Kind` is not among them: a drawn rectangle cannot become an arrow, because the two are
 * different classes carrying different geometry, and "convert this shape" is a feature
 * rather than a restyle. `Shapes` is excluded for the obvious reason — it *is* the list,
 * and rewriting it here would recurse.
 *
 * Null for "nothing to apply", including a selection naming a shape the document no longer
 * holds — an id outlives the undo that took its shape away.
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
    if (spec != MarkupColorParam && spec != sizeParam) return null

    return params.with(
        MarkupTool.Shapes,
        shapes.map { shape ->
            if (shape.id == selectedId) {
                shape.styled(color = params[MarkupColorParam], width = params[sizeParam])
            } else {
                shape
            }
        }
    )
}

/** Which of the two size rows this shape's `width` is spelled by. */
private fun sizeParamFor(shape: MarkupShape): FloatParam =
    if (shape is MarkupShape.Text) MarkupTextSizeParam else MarkupWidthParam
