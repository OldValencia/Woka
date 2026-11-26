package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.engine.video.CLIP_DELETE_TAG
import com.woka.feature.editor.engine.video.CLIP_MORE_TAG
import com.woka.feature.editor.engine.video.CLIP_MUTE_TAG
import com.woka.feature.editor.engine.video.CLIP_SPLIT_TAG
import com.woka.feature.editor.engine.video.ClipPanel
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.WHOLE_SOURCE_ID
import com.woka.support.FixtureTimeline
import com.woka.support.MainDispatcherRule
import com.woka.support.onTimeline
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SPEED_TAG = "param-speed"

/** Two clips of the 3 s film, the second selectable as `b` (layer 1); or a still frame as `f`. */
private val twoClips = EditDocument(listOf(TrimTool.opFor(listOf(TrimClip("a", 0L, 1_500L), TrimClip("b", 1_500L, 3_000L, key = 1)))))
private val withStill = EditDocument(listOf(TrimTool.opFor(listOf(TrimClip("a", 0L, 3_000L), TrimClip("f", 1_000L, 1_000L, key = 1, holdMs = 2_000L)))))

/**
 * The Clip tab's panel over a real player (RD-302, `#editor/video` → Clip): Speed on a log track
 * with 1× at the centre, Mute sound, Split | Delete and More actions — each one history step.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ClipPanelUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private fun panel(document: EditDocument = EditDocument(), selected: String, block: ComposeUiTest.(FixtureTimeline) -> Unit) =
        onTimeline(document = document, panel = { holder, edits -> ClipPanel(holder, edits.document, edits.selectedId) }) { _, edits ->
            runOnIdle { edits.select(selected) }
            waitForIdle()
            block(edits)
        }

    @Test
    fun `a tap at three quarters sets 2x as one step, a right-click puts 1x back`() = panel(selected = WHOLE_SOURCE_ID) { edits ->
        onNodeWithTag("$SPEED_TAG-field").assertTextEquals("1")
        onNodeWithTag(SPEED_TAG).performMouseInput { click(centerLeft.copy(x = width * 0.75f)) }
        waitForIdle()
        assertEquals(2f, assertNotNull(TrimTool.clipsIn(edits.document)).single().speed)
        assertEquals(1, edits.commits)
        onNodeWithTag("$SPEED_TAG-field").assertTextEquals("2")

        onNodeWithTag(SPEED_TAG).performMouseInput { rightClick(center) }
        waitForIdle()
        assertNull(TrimTool.clipsIn(edits.document), "back at 1x the film is written as uncut")
        assertEquals(2, edits.commits)
    }

    @Test
    fun `mute sound writes the clip's own sound`() = panel(twoClips, selected = "b") { edits ->
        onNodeWithTag(CLIP_MUTE_TAG).performClick()
        waitForIdle()
        val (key, ops) = assertNotNull(edits.pasted)
        assertEquals(1, key)
        assertTrue(ops.single().params[SoundTool.Mute], "the clip was not muted")
    }

    @Test
    fun `delete takes the clip out and the hole closes`() = panel(twoClips, selected = "b") { edits ->
        onNodeWithTag(CLIP_DELETE_TAG).performClick()
        waitForIdle()
        assertEquals(listOf("a"), assertNotNull(TrimTool.clipsIn(edits.document)).map { it.id })
        assertNull(edits.selectedId)
    }

    @Test
    fun `more actions opens the clip menu`() = panel(twoClips, selected = "b") {
        onNodeWithTag(CLIP_MORE_TAG).performClick()
        waitForIdle()
        onNodeWithText("Duplicate").assertExists()
    }

    @Test
    fun `a still frame has the buttons and no speed or sound`() = panel(withStill, selected = "f") {
        onNodeWithTag(CLIP_SPLIT_TAG).assertExists()
        onNodeWithTag(CLIP_DELETE_TAG).assertExists()
        onNodeWithTag(SPEED_TAG).assertDoesNotExist()
        onNodeWithTag(CLIP_MUTE_TAG).assertDoesNotExist()
    }
}
