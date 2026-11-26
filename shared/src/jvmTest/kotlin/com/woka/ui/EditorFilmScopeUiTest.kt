package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.feature.editor.core.ClipScope
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.pickClip
import com.woka.feature.editor.shell.EditorFrameControls
import com.woka.support.EditorHarness
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureEngine
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull

private const val SCOPE_TAG = "editor-frame-scope"
private const val WHOLE_TAG = "editor-scope-whole"
private const val CLIP_TAG = "editor-scope-clip"

/** A film for the shell: three clips once [cut], the second 2.4 s long, or one uncut. */
private class FilmFixture(private val cut: Boolean) : EditorEngine by FixtureEngine(frames = EditorFrames(clips = true)) {
    override fun framesIn(document: EditDocument): EditorFrames = EditorFrames(count = if (cut) 3 else 1, clips = true)

    override fun clipScopeOf(document: EditDocument, id: String?): ClipScope? =
        if (cut && id == "c2") ClipScope(number = 2, lengthMs = 2_400) else null
}

/**
 * A film's scope line at the inspector's head (RD-204, `#editor/video`): `Whole video`, and
 * with a clip selected `Whole video › Clip 2  2.4 s`, whose first crumb takes the selection
 * back (ME-221). The film is a fixture — the clip arithmetic is `VideoEditorClips`'.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorFilmScopeUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)

    private fun film(cut: Boolean, selected: String?, block: ComposeUiTest.(EditorHarness) -> Unit) =
        onEditor(listOf(tool)) { harness ->
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
                        )
                    )
                }
                if (selected != null) runOnIdle { harness.session.pickClip(selected, 1) }
                waitForIdle()
                block(harness)
            }
        }

    @Test
    fun `an uncut film reads whole video, with nothing to go back to`() =
        film(cut = false, selected = null) {
            onNode(hasTestTag(SCOPE_TAG) and hasAnyAncestor(hasTestTag("editor-panel"))).assertIsDisplayed()
            onNodeWithText("Whole video").assertIsDisplayed()
            onNodeWithTag(WHOLE_TAG).assertDoesNotExist()
        }

    @Test
    fun `a selected clip is named with its length`() =
        film(cut = true, selected = "c2") {
            onNodeWithTag(WHOLE_TAG).assertIsDisplayed()
            onNodeWithTag(CLIP_TAG).assertTextContains("Clip 2", substring = true)
            onNodeWithTag(CLIP_TAG).assertTextContains("2.4 s", substring = true)
        }

    @Test
    fun `whole video takes the selection back`() =
        film(cut = true, selected = "c2") { harness ->
            onNodeWithTag(WHOLE_TAG).performClick()
            waitForIdle()

            assertNull(harness.session.clipSelection.value, "the clip stayed selected")
            onNodeWithTag(CLIP_TAG).assertDoesNotExist()
        }
}
