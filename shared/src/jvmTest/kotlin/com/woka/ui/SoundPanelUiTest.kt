package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.moveToFrame
import com.woka.feature.editor.core.pickClip
import com.woka.feature.editor.core.useLinearHistory
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.support.EditorHarness
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Sound tab as the prototype draws it (RD-302, `panels.js` `soundPanel`): Mute beside
 * Volume in one well, the fades under `Fades`, and Even loudness for the whole film only.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SoundPanelUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private fun sound(block: ComposeUiTest.(EditorHarness) -> Unit) = onEditor(listOf(SoundTool)) { harness ->
        onUi {
            val engine = FixtureEngine(kind = MediaKind.Video, frames = EditorFrames(clips = true))
            // As `EditorHost` opens a film: linear, on no clip (`VideoEditorClips.openingFrame`).
            harness.session.useLinearHistory(emptySet())
            harness.session.moveToFrame(NO_CLIP, EditScope.AllFrames)
            setContent { EditorUnderShell(harness = harness, accessories = TopBarAccessoriesState(), engine = engine) }
            waitForIdle()
            block(harness)
        }
    }

    @Test
    fun `Mute and Volume share a well, the fades are headed, Even loudness is the film's`() = sound {
        onAllNodesWithTag("param-well").assertCountEquals(3)
        onNodeWithText("Fades").assertExists()
        onNodeWithTag("param-even").assertExists()
        // Three wells for five rows: Mute stands in Volume's, not on a plate of its own.
        onNode(hasTestTag("param-mute") and hasAnyAncestor(hasTestTag("param-well"))).assertExists()
        onNode(hasTestTag("param-volume") and hasAnyAncestor(hasTestTag("param-well"))).assertExists()
    }

    @Test
    fun `the whole row flips Mute`() = sound { harness ->
        onNodeWithTag("param-mute").performClick()
        waitForIdle()
        assertTrue(harness.session.document.value.opsOf(SoundTool.manifest.id).last().params[SoundTool.Mute])
    }

    @Test
    fun `a selected clip has no Even loudness`() = sound { harness ->
        runOnIdle { harness.session.pickClip("c2", 1) }
        waitForIdle()
        onNodeWithTag("param-even").assertDoesNotExist()
        onAllNodesWithTag("param-well").assertCountEquals(2)
        runOnIdle { harness.session.pickClip(null, NO_CLIP) }
        waitForIdle()
        onNodeWithTag("param-even").assertExists()
    }
}
