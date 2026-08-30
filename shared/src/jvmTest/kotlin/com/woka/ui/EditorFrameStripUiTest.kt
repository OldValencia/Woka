package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.shell.EditorFrameControls
import com.woka.fixtures.ANIM_FRAME_COUNT
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Walking a frame set without leaving the editor (ME-158).
 *
 * Over a real copy of the corpus's burst, because the editor's strip reads the file
 * itself — deliberately **not** through `rememberFrameStrip`, which models the
 * viewer's strip and appends a Live Photo's motion sibling the editor has no decoder
 * for. A supplied frame list would have tested the mounting and not the reading.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorFrameStripUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)

    private fun copyOf(fixture: String): File {
        val dir = Files.createTempDirectory("woka-editor-strip").toFile()
        return File(dir, fixture).apply { writeBytes(SampleMedia.file(fixture).readBytes()) }
    }

    private fun onStrip(
        fixture: String,
        frames: EditorFrames,
        onSelectFrame: (Int) -> Unit = {},
        block: ComposeUiTest.() -> Unit
    ) = onEditor(listOf(tool), file = copyOf(fixture)) { harness ->
        onUi {
            setContent {
                EditorUnderShell(
                    harness = harness,
                    accessories = TopBarAccessoriesState(),
                    frameControls = EditorFrameControls(
                        frames = frames,
                        scope = EditScope.AllFrames,
                        frameIndex = 0,
                        onScopeChange = {},
                        onSelectFrame = onSelectFrame
                    )
                )
            }
            waitForIdle()
            block()
        }
    }

    @Test
    fun `a burst gets one thumbnail per frame, read from the file itself`() =
        onStrip(BURST_HEIC, EditorFrames(count = BURST_FRAME_COUNT)) {
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithContentDescription("Frame $BURST_FRAME_COUNT")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            (1..BURST_FRAME_COUNT).forEach {
                onAllNodesWithContentDescription("Frame $it").assertCountEquals(1)
            }
        }

    @Test
    fun `clicking a frame asks for that frame`() {
        var chosen = -1
        onStrip(
            BURST_HEIC,
            EditorFrames(count = BURST_FRAME_COUNT),
            onSelectFrame = { chosen = it }
        ) {
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithContentDescription("Frame 3").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithContentDescription("Frame 3").performClick()
        }
        assertEquals(2, chosen, "the strip asked for the wrong picture")
    }

    /** The same rule as the scope switch: one picture, or one that moves, gets nothing. */
    @Test
    fun `a photograph gets no strip`() = onStrip(STILL_JPG, EditorFrames()) {
        onAllNodesWithContentDescription("Frame 1").assertCountEquals(0)
    }

    @Test
    fun `an animation gets none either`() =
        onStrip(ANIM_GIF, EditorFrames(count = ANIM_FRAME_COUNT, playsAsAnimation = true)) {
            onAllNodesWithContentDescription("Frame 1").assertCountEquals(0)
        }
}
