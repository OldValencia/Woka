package com.woka.feature.editor.tools.markup

import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamFace
import com.woka.feature.editor.core.ToggleSetParam

/**
 * Type's own controls — the size it is set at, and the faces it wears.
 *
 * Split from `MarkupPen.kt` at the size mandate when the faces spec grew a sample for the
 * compact button (ME-155), at the seam that was already there: what a *stroke* is drawn
 * with on that side, what *type* is drawn with on this one. Nothing moved changed.
 */

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
 * The four faces, as a **multi-select** chip row (ME-151, asked for 2026-08-26).
 *
 * The one spec here that is not a `ChoiceParam`, and the reason `ToggleSetParam` exists:
 * a shape is one of five kinds, but a caption may be bold *and* underlined, so the row
 * above lights one chip and this one lights any number.
 *
 * Labelled `Style` rather than `Faces` — the panel already says `Shape`, `Color`, `Fill`,
 * `Corner` and `Text size`, and none of those is a term of art either.
 */
internal val MarkupFacesParam = ToggleSetParam(
    key = "faces",
    label = "Style",
    options = TextFace.entries.toList(),
    default = emptySet(),
    iconOf = { face ->
        when (face) {
            TextFace.Bold -> Icons.Static.TextBold
            TextFace.Italic -> Icons.Static.TextItalic
            TextFace.Underline -> Icons.Static.TextUnderline
            TextFace.Strikethrough -> Icons.Static.TextStrikethrough
        }
    },
    // What the compact button draws, in whatever is lit (ME-155) — so `[Aa▾]` closed
    // reports the weight, the slant and the lines without being opened.
    sample = "Aa",
    faceOf = { face ->
        when (face) {
            TextFace.Bold -> ParamFace.Bold
            TextFace.Italic -> ParamFace.Italic
            TextFace.Underline -> ParamFace.Underline
            TextFace.Strikethrough -> ParamFace.Strikethrough
        }
    }
)
