package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import java.io.File

/**
 * The session for a file, and the two facts it needs about the frames in it (ME-169).
 *
 * **Keyed on the file alone.** It was rebuilt per frame, which is what made walking to
 * another picture discard the work on this one — the discard dialog that asked about it
 * was ME-158 being honest about a limitation, not a feature. The engine is still keyed on
 * the frame, because it draws one frame's pixels; the session is told to look elsewhere
 * instead.
 *
 * Its own file because `EditorHost` is at the size mandate and this is a whole idea:
 * what the session is built from, and nothing about how the editor is laid out.
 */
@Composable
internal fun rememberEditorSession(
    path: String,
    frameIndex: Int,
    file: File,
    engine: EditorEngine
): EditorSession = remember(path) {
    EditorSession(
        media = MediaItem.Image(file),
        // Opened on what the file already holds (ME-134): a session starting empty made
        // the second `Done` on a photograph replace the first one's edit instead of
        // continuing it.
        initial = engine.storedDocument(),
        openedOn = frameIndex,
        storedFor = engine::storedDocumentIn,
        storedBase = engine.storedBase()
    )
}
