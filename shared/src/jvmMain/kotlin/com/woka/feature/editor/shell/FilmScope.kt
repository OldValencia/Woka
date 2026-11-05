package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.useLinearHistory

/** The tools whose work is the whole film's (ME-221): a cut, a frame's geometry, its size. */
private val WHOLE_FILM = setOf(ToolDomain.Time, ToolDomain.Geometry, ToolDomain.Output)

/**
 * The scope `Done` writes into, held for the sitting by `EditorHost`.
 *
 * A still's is the switch's, seeded from the file (ME-156) once per sitting. **A film's
 * follows its open clip** (ME-221): the selected tab is the scope, so this only mirrors the
 * session — and the session is made linear here, once, before anything is edited.
 */
@Composable
internal fun rememberEditScope(
    path: String,
    engine: EditorEngine,
    session: EditorSession,
    registry: EditorToolRegistry
): MutableState<EditScope> {
    val scope = remember(path) { mutableStateOf(engine.frames.storedScope) }
    if (!engine.frames.clips) return scope
    LaunchedEffect(session) {
        val shared = registry.tools(engine.kind).value.filter { it.manifest.domain in WHOLE_FILM }
        session.useLinearHistory(shared.map { it.manifest.id }.toSet())
        session.focus.collect { scope.value = if (it == NO_CLIP) EditScope.AllFrames else EditScope.SingleFrame }
    }
    return scope
}

/** Whether opening [domain] clears a film's clip selection: Crop, Transform, Resize, Cover. */
internal fun clearsClipSelection(domain: ToolDomain?): Boolean =
    domain == ToolDomain.Geometry || domain == ToolDomain.Output
