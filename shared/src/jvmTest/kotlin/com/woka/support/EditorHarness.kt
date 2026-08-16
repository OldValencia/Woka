package com.woka.support

import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.EditorSession
import java.io.File

/**
 * A session over a nominal image, closed however the block ends.
 *
 * Constructed **inside** the block rather than in a test's field initialiser: the
 * session captures `Dispatchers.Main` when it builds its scope, and a field
 * initialiser runs before [MainDispatcherRule] has installed the test dispatcher,
 * so its `stateIn(…, Eagerly)` derivations would collect on the real Main and
 * never update. `onFolder` exists for the same reason.
 *
 * The file need not exist. Nothing in ME-004 reads it — the session is
 * media-agnostic by design (§3.3) and only the engine ever opens anything.
 */
fun onEditorSession(block: (EditorSession) -> Unit) {
    val session = EditorSession(MediaItem.Image(File("photo.jpg")))
    try {
        block(session)
    } finally {
        session.close()
    }
}
