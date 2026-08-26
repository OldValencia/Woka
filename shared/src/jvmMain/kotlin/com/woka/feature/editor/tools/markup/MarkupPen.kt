package com.woka.feature.editor.tools.markup

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.FloatParam

/**
 * The pen — what the *next* shape is drawn with (ME-114).
 *
 * Split from `MarkupTool` for the size mandate, at the seam that was already there: the
 * manifest and `stage()` on one side, the controls on this one. What choosing one of them
 * does to the shape already selected went the same way at ME-146 — `MarkupRestyle`.
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
 * The plate behind a caption, and **`0L` — fully transparent — as its first swatch and its
 * default** (ME-146).
 *
 * `ColorParam` whole, rather than a nullable colour spec or a `BoolParam` beside a colour
 * row: a caption without a plate is the common case, so "none" has to be one press away,
 * and `0L` is already a legal `ColorValue`. `ColorSwatch` draws a zero-alpha value as a
 * diagonal slash, which is the entire cost of expressing it. A switch plus a colour row
 * that silently does nothing while it is off is the defect ME-122 already fixed once for
 * the size rows.
 */
internal val MarkupFillParam = ColorParam(
    key = "fill",
    label = "Fill",
    swatches = listOf(0L) + MARKUP_SWATCHES,
    default = 0L
)

/**
 * How round that plate's corners are (asked for 2026-08-27).
 *
 * **A per cent of the type's size, not a count of pixels.** A radius in pixels would have
 * to be multiplied by ME-148's corner-drag ratio like the font size and the wrap width;
 * expressed against the size it already is, it scales for free and one field fewer takes
 * part in the drag. It is also the number that stays meaningful across a 12 px caption and
 * a 512 px one, which a pixel radius is not.
 */
internal val MarkupCornerParam =
    FloatParam("corner", "Corner", 0f..100f, default = 25f, unit = "%")
