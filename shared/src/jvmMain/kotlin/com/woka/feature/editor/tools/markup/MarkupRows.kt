package com.woka.feature.editor.tools.markup

import androidx.compose.ui.unit.IntSize
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
internal fun markupRows(params: ParamSet, sourceSize: IntSize): List<ParamSpec<*>> {
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
    return MarkupTool.specs(sourceSize).filterNot { it.key in hidden.map { spec -> spec.key } || it.isTiming() }
}

/** [markupRows], and for a film with a shape selected its timing rows (ME-263), ranged to the film. */
internal fun markupRows(params: ParamSet, sourceSize: IntSize, selectedId: String?, timelineMs: Long?): List<ParamSpec<*>> {
    val pen = markupRows(params, sourceSize)
    val timed = selectedId != null && timelineMs != null && timelineMs > 0L &&
        params[MarkupTool.Shapes].any { it.id == selectedId }
    return if (timed) pen + timingRows(timelineMs) else pen
}

/**
 * The two size rows, re-defaulted for this picture (ME-159); everything else untouched.
 *
 * Substituted here rather than in `MarkupTool.params` because the declared list is what
 * `hidden` above is expressed against, and because every other consumer of a spec — the
 * writer, the fold, the saved document — reaches it by **key**, which a copy keeps.
 *
 * **A derived copy is not `==` the declared spec**, and `markupLines` and `markupFolded`
 * both run on what this returns. They compare by key for that reason; comparing by
 * identity silently dropped both size controls off the compact bar.
 */
internal fun ParamSpec<*>.sizedFor(sourceSize: IntSize): ParamSpec<*> = when (this) {
    MarkupTool.TextSize -> textSizeSpecFor(sourceSize)
    MarkupTool.Width -> widthSpecFor(sourceSize)
    else -> this
}
