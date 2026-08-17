package com.woka.support

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaItem
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.fold
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

/**
 * A session, a registry over a given tool set, and the repository behind it —
 * everything needed to drive an edit end to end without a window.
 *
 * A harness a test *uses*, never a base class it extends (`FolderHarness`,
 * `ViewerHarness`). The three members are public because a test asserts on them
 * directly; the methods below exist only for the sequences that would otherwise be
 * repeated verbatim in every test.
 */
class EditorHarness(
    val session: EditorSession,
    val registry: EditorToolRegistry,
    val settings: SettingsRepositoryImpl
) {

    /**
     * Preview then commit — what the shell does for one completed gesture.
     *
     * The two are deliberately not collapsed inside `EditorSession`: keeping them
     * separate is what makes a 200-frame drag one undo step (§3.1), and a test of
     * *that* calls `preview` itself rather than reaching for this.
     */
    fun apply(op: ToolOp, cardinality: ToolCardinality = ToolCardinality.Single) {
        session.preview(op)
        session.commit(cardinality)
    }

    fun undo() = session.undo()

    /** What the canvas would draw right now, operation in flight included. */
    fun recipe(sourceSize: IntSize = FOLD_SOURCE): RasterRecipe =
        fold(session.document.value, registry, sourceSize)
}

/**
 * [EditorHarness] over [tools], on a throwaway storage root.
 *
 * Both the session and the repository are built **inside** the block, for the
 * reason `onEditorSession` documents: each captures `Dispatchers.Main` as it builds
 * its scope, and a field initialiser runs before [MainDispatcherRule] installs the
 * test dispatcher.
 */
fun onEditor(tools: List<EditorTool>, block: (EditorHarness) -> Unit) = withTempStorageRoot {
    val settings = SettingsRepositoryImpl()
    val session = EditorSession(MediaItem.Image(File("photo.jpg")))
    try {
        block(EditorHarness(session, EditorToolRegistry(tools, settings), settings))
    } finally {
        session.close()
    }
}
