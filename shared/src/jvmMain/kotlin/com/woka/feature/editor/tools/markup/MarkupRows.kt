package com.woka.feature.editor.tools.markup

import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec

/**
 * Which of the tool's rows the panel currently shows (ME-122, ME-146, ME-151).
 *
 * Split out of `MarkupTool.kt` at the size mandate when the faces row arrived, at the seam
 * that was already there: what the tool *is* — its manifest, its specs, its stage — on that
 * side, and which of those specs are worth drawing right now on this one. Nothing moved
 * changed.
 *
 * **One size row at a time, the plate only where there is one, and the faces only on
 * type.** A stroke width does nothing to a caption, a font size does nothing to an arrow
 * and a background does nothing to either, and a row that changes nothing when used is
 * worse than a missing one — it reads as broken (reported by hand 2026-08-26).
 *
 * Keyed on the **pen** rather than on what happens to be selected, because the pen is what
 * the panel is describing: `Kind` is the chooser directly above these rows, and a row that
 * moved with the *selection* would appear and vanish under a click on the picture that the
 * inspector gives no sign of. Restyling a caption therefore wants the text pen chosen,
 * which is where a caption is drawn from anyway.
 */
internal fun markupRows(params: ParamSet): List<ParamSpec<*>> {
    val typed = params[MarkupTool.Kind] == MarkupKind.Text
    // **`Corner` needs a plate to round, not merely a caption to sit under.** With no
    // fill chosen, dragging it changes nothing that can be seen — the same rule the size
    // rows follow, applied one level down: the row is keyed on the value above it rather
    // than on the pen.
    val plated = typed && (params[MarkupTool.Fill] ushr 24) != 0L
    val hidden = when {
        typed -> if (plated) setOf(MarkupTool.Width) else setOf(MarkupTool.Width, MarkupTool.Corner)
        else -> setOf(MarkupTool.TextSize, MarkupTool.Fill, MarkupTool.Corner, MarkupTool.Faces)
    }
    return MarkupTool.params.filterNot { it in hidden }
}
