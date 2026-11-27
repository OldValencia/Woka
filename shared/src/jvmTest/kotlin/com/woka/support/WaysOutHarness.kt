package com.woka.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorExport
import com.woka.feature.editor.shell.EditorHostControls
import com.woka.feature.editor.tools.resize.ResizeTool
import java.io.File
import kotlin.test.assertEquals

/** A copy's export and a film's frame, as the real engines name theirs (RD-401). */
val COPY_EXPORT = EditorExport(id = "copy", title = "Render…", summary = "")
val FRAME_EXPORT = EditorExport(id = "frame", title = "Frame", summary = "", progress = "Saving frame", frame = true)

/**
 * [FixtureEngine] with exports, recording each one it is asked to run — what `Create a copy`
 * and `Save frame as photo` hand the engine, size and moment included.
 */
class ExportingEngine(
    private val base: FixtureEngine,
    override val exports: List<EditorExport> = listOf(COPY_EXPORT)
) : EditorEngine by base {

    val exported = mutableListOf<EditorExport>()

    override suspend fun export(export: EditorExport, layers: EditLayers, progress: (Float) -> Unit): EditSaveResult {
        exported += export
        return EditSaveResult.Saved("copy.jpg")
    }
}

/**
 * The editor's ways out with no screen around them: `EditorHostControls` over [engine] and
 * the harness's session, and three bare targets — `host-done`, `host-copy`, `host-leave` —
 * standing in for the header's `Done`, `Create a copy…` and `Cancel`; `host-frame` for the
 * timeline's `Save frame as photo…`.
 */
@Composable
fun WaysOutHost(harness: EditorHarness, engine: EditorEngine, onClose: () -> Unit = {}) {
    EditorHostControls(
        engine = engine,
        session = harness.session,
        file = File("photo.jpg"),
        editScope = EditScope.AllFrames,
        carriesStoredEdit = false,
        onFrameIndex = {},
        onClose = onClose,
        onSaved = {},
        keys = null
    ) { actions ->
        Row {
            Box(Modifier.size(20.dp).clickable { actions.done() }.testTag("host-done"))
            Box(Modifier.size(20.dp).clickable { actions.choices.onCopy() }.testTag("host-copy"))
            Box(Modifier.size(20.dp).clickable { actions.leave() }.testTag("host-leave"))
            Box(Modifier.size(20.dp).clickable { actions.choices.onSaveFrame?.invoke() }.testTag("host-frame"))
        }
    }
}

/**
 * `Create a finished copy` over [base], opened from the host; [block] gets the engine that
 * records the exports. Afterwards the document's own Resize must be as [initial] left it —
 * a copy's size is the copy's alone.
 */
@OptIn(ExperimentalTestApi::class)
fun copying(base: FixtureEngine, initial: EditDocument = EditDocument(), block: ComposeUiTest.(ExportingEngine) -> Unit) =
    onEditor(emptyList(), initial = initial) { harness ->
        val exports = if (base.kind == MediaKind.Video) listOf(COPY_EXPORT, FRAME_EXPORT) else listOf(COPY_EXPORT)
        val engine = ExportingEngine(base, exports)
        onUi {
            setContent { WaysOutHost(harness, engine) }
            onNodeWithTag("host-copy").performClick()
            waitForIdle()
            onNodeWithText("Create a finished copy").assertIsDisplayed()
            block(engine)
            val resize = ResizeTool.manifest.id
            assertEquals(initial.opsOf(resize), harness.session.document.value.opsOf(resize), "a copy rewrote the saved Resize")
        }
    }

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.createCopy() {
    onNodeWithTag("editor-copy-confirm").performClick()
    waitForIdle()
}

/** The size group's two fields read [width] and [height]. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.sizeFields(width: String, height: String) {
    onNodeWithTag("output-width").assertTextEquals(width)
    onNodeWithTag("output-height").assertTextEquals(height)
}
