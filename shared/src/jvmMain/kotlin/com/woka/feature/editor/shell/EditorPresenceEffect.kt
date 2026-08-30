package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.woka.app.shell.EditorPresence
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession

/**
 * That an editor is open, published — and the session's lifetime, which is the same
 * effect because they begin and end together.
 *
 * Split out of `EditorHost` when ME-156 and ME-158 both needed room in it. The seam
 * was already there: nothing here reads the document, the tools or the frame, and
 * everything here is about the editor *existing*.
 *
 * **The session's `close()` is the obligation ME-004 made explicit**: it builds a
 * `CoroutineScope` and whoever constructs it must close it. Keyed on the session, so
 * a rebuilt one — a different file, or a different frame (ME-158) — closes the one it
 * replaced.
 *
 * [leaveForVideoAction] is held through [rememberUpdatedState] so the lambda published
 * to `EditorPresence` always runs the current callbacks and reads the document when it
 * is called rather than now (ME-124).
 */
@Composable
internal fun EditorPresenceEffect(
    engine: EditorEngine,
    session: EditorSession,
    name: String,
    scope: EditScope,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
) {
    val leaveForVideo by rememberUpdatedState(
        leaveForVideoAction(engine, session, name, scope, onClose, onSaved)
    )
    DisposableEffect(session) {
        EditorPresence.opened { leaveForVideo() }
        onDispose {
            EditorPresence.closed()
            session.close()
        }
    }
}
