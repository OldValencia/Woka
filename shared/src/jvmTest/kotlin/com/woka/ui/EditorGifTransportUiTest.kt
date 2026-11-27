package com.woka.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.core.ui.components.frames.FrameTransport
import com.woka.feature.editor.FrameClockStart
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.SampleMedia
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A GIF in the editor (RD-501, `#editor/gif`): the strip and the viewer's transport, moving
 * the engine's own clock — never the document, and with no scope line (ME-138).
 *
 * Over a real copy of the corpus's animation and a real image engine, because the band
 * appears only once the engine has read the frames, and the clock it moves is the engine's.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorGifTransportUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)

    private fun copy(): File {
        val dir = Files.createTempDirectory("woka-editor-gif").toFile()
        return File(dir, ANIM_GIF).apply { writeBytes(SampleMedia.file(ANIM_GIF).readBytes()) }
    }

    /** Opened as the viewer left it: paused on frame 5, Loop off. */
    private fun onGif(block: ComposeUiTest.(FrameTransport, () -> EditDocument, ImageEditorEngine) -> Unit) {
        val file = copy()
        onEditor(listOf(tool), file = file) { harness ->
            val clock = FrameClockStart(playing = false, loop = false)
            val engine = ImageEditorEngine(file, 5, harness.registry, EditCapability.Recorded, clock)
            onUi {
                setContent {
                    EditorUnderShell(harness = harness, accessories = TopBarAccessoriesState(), engine = engine)
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithTag("frame-transport").fetchSemanticsNodes().isNotEmpty()
                }
                block(assertNotNull(engine.frameTransport), { harness.session.document.value }, engine)
            }
        }
    }

    @Test
    fun `the editor opens on the viewer's clock, at 1x`() = onGif { transport, _, _ ->
        assertEquals(5, transport.frameIndex)
        assertFalse(transport.isPlaying, "a paused GIF started playing in the editor")
        assertFalse(transport.loop, "Loop was not carried")
        assertEquals(1f, transport.speed)
        onAllNodesWithText("All frames").assertCountEquals(0)
    }

    @Test
    fun `a strip click pauses there and leaves the edit alone`() = onGif { transport, document, _ ->
        onNodeWithTag("frame-transport-play").performClick()
        waitForIdle()
        assertTrue(transport.isPlaying)
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithContentDescription("Frame 3").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithContentDescription("Frame 3").performClick()
        waitForIdle()
        assertFalse(transport.isPlaying, "a frame chosen by hand kept playing")
        assertEquals(2, transport.frameIndex)
        assertTrue(document().isEmpty, "walking the frames wrote an edit")
    }

    @Test
    fun `the step buttons and the keys move one frame`() = onGif { transport, _, engine ->
        onNodeWithTag("frame-transport-forward").performClick()
        waitForIdle()
        assertEquals(6, transport.frameIndex)
        assertTrue(engine.timelineKey(Key.Comma))
        assertTrue(engine.timelineKey(Key.Comma))
        assertEquals(4, transport.frameIndex)
    }
}
