package com.woka.support

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.ClipScope
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.pickClip
import com.woka.feature.editor.shell.CanvasKeys
import com.woka.feature.editor.shell.EditorFrameControls

/** A film for the shell: three clips once [cut], the second 2.4 s long, or one uncut. */
internal class FilmFixture(private val cut: Boolean) :
    EditorEngine by FixtureEngine(kind = MediaKind.Video, frames = EditorFrames(clips = true)) {
    override fun framesIn(document: EditDocument): EditorFrames = EditorFrames(count = if (cut) 3 else 1, clips = true)

    override fun clipScopeOf(document: EditDocument, id: String?): ClipScope? =
        if (cut && id == "c2") ClipScope(number = 2, lengthMs = 2_400) else null
}

/**
 * The editor over a [FilmFixture] with the scope line on (RD-204, RD-302): [selected] picked as
 * layer 1 before [block] runs, [keys] handed to the screen so a test can press Esc's half.
 */
@OptIn(ExperimentalTestApi::class)
internal fun onFilm(
    tools: List<EditorTool>,
    cut: Boolean,
    selected: String?,
    keys: CanvasKeys? = null,
    block: ComposeUiTest.(EditorHarness) -> Unit
) = onEditor(tools) { harness ->
    onUi {
        val engine = FilmFixture(cut)
        setContent {
            EditorUnderShell(
                harness = harness,
                accessories = TopBarAccessoriesState(),
                engine = engine,
                frameControls = EditorFrameControls(
                    frames = engine.frames,
                    scope = EditScope.AllFrames,
                    frameIndex = 0,
                    onScopeChange = {},
                    onSelectFrame = {}
                ),
                canvasKeys = keys
            )
        }
        if (selected != null) runOnIdle { harness.session.pickClip(selected, 1) }
        waitForIdle()
        block(harness)
    }
}
