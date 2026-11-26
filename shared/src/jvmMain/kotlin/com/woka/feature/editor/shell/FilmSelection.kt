package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.pickClip

/**
 * What a film's editor has selected — a clip (the session's, ME-221) or a Markup shape (the
 * canvas's, ME-114) — and the two ways back to `Whole video` (RD-302, `app.js` `scope-all` and
 * the Esc handler). Markup is the whole film's, so the two never stand together: opening Markup
 * clears the clip (`clearsClipSelection`).
 *
 * Built every composition from the screen's own state; it holds nothing.
 */
internal class FilmSelection(
    private val session: EditorSession,
    private val tabs: EditorTabState,
    private val shapes: ToolSelection,
    private val film: Boolean
) {
    /**
     * The crumb `Whole video`: drops the clip and the shape, and leaves a tab that was about the
     * selection — Clip, or the tab holding the shape — for the first one (Styles).
     */
    fun wholeVideo() {
        val open = tabs.tab
        val about = open != null && (open.canvasTool.manifest.clipOnly || (shapes.id != null && open.canvasTool is CanvasTool))
        shapes.id = null
        session.pickClip(null, NO_CLIP)
        if (about) tabs.shown.firstOrNull { !it.canvasTool.manifest.clipOnly }?.let { tabs.choose(it.id) }
    }

    /**
     * Esc: the shape first, then the clip — the tab stays, unless it was Clip, which goes with
     * the clip. False with nothing selected, or on a still, so Esc goes on to close the editor.
     */
    fun back(): Boolean {
        if (!film) return false
        if (shapes.id != null) {
            shapes.id = null
            return true
        }
        if (session.clipSelection.value == null) return false
        session.pickClip(null, NO_CLIP)
        return true
    }

    /** The open tab's selection by name, for the scope line — a Markup shape's; null otherwise. */
    fun shapeLabel(engine: EditorEngine, document: EditDocument): String? {
        if (!film) return null
        val tool = tabs.tab?.canvasTool as? CanvasTool ?: return null
        return tool.selectionLabel(paramsOf(document, tool, engine.sourceSize), shapes.id)
    }
}

/** The screen's [FilmSelection], with Esc's half handed to the editor's keyboard. */
@Composable
internal fun rememberFilmSelection(
    session: EditorSession,
    tabs: EditorTabState,
    shapes: ToolSelection,
    film: Boolean,
    keys: CanvasKeys?
): FilmSelection {
    val selection = FilmSelection(session, tabs, shapes, film)
    SideEffect { keys?.onBack = selection::back }
    return selection
}
