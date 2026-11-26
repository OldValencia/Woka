package com.woka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.shell.EditorFrameControls
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.startKoinOnce
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test

private const val SCOPE_TAG = "editor-frame-scope"

private const val NOTE_TAG = "editor-scope-note"

/**
 * The switch that says who the edit is for (ME-156), at the level the user meets it — at
 * the inspector's head since RD-204, its consequence said only when work would be lost.
 *
 * The frames are supplied rather than decoded: what a real file reports is
 * `FrameScopeSaveTest`'s job, and this layer must never open a HEIC. What is asserted
 * here is the two things the ticket is actually about — **when** the control exists,
 * and whether it states the consequence before the click.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorFrameScopeUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** The pill inside the editor's top bar resolves its settings from Koin. */
    @BeforeTest
    fun koin() = startKoinOnce()

    private val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)

    private fun burst(overridden: Set<Int> = emptySet()) =
        EditorFrames(count = 5, overridden = overridden)

    /**
     * Composes the screen with a scope the strip itself moves, which is the only way
     * to assert that the consequence line follows the choice rather than the argument.
     */
    private fun scoped(
        frames: EditorFrames,
        frameIndex: Int = 0,
        initial: EditScope = EditScope.AllFrames,
        block: ComposeUiTest.() -> Unit
    ) = onEditor(listOf(tool)) { harness ->
        onUi {
            setContent {
                var scope by remember { mutableStateOf(initial) }
                EditorUnderShell(
                    harness = harness,
                    accessories = TopBarAccessoriesState(),
                    frameControls = EditorFrameControls(
                        frames = frames,
                        scope = scope,
                        frameIndex = frameIndex,
                        onScopeChange = { scope = it },
                        onSelectFrame = {}
                    )
                )
            }
            waitForIdle()
            block()
        }
    }

    /** The overwhelmingly common case, which must cost nothing and show nothing. */
    @Test
    fun `a single picture is offered no scope at all`() =
        scoped(EditorFrames()) { onNodeWithTag(SCOPE_TAG).assertDoesNotExist() }

    /** A GIF is *watched*: an edit on one frame of eight is a flicker (ME-140). */
    @Test
    fun `an animation is offered none either`() =
        scoped(EditorFrames(count = 8, playsAsAnimation = true)) {
            onNodeWithTag(SCOPE_TAG).assertDoesNotExist()
        }

    /** Above the tabs, in the inspector — nothing over the picture's top edge (RD-204). */
    @Test
    fun `a burst gets the switch at the head of the inspector`() =
        scoped(burst(), frameIndex = 2) {
            onNode(hasTestTag(SCOPE_TAG) and hasAnyAncestor(hasTestTag("editor-panel"))).assertIsDisplayed()
            onNodeWithText("All frames").assertIsDisplayed()
            onNodeWithText("Frame 3").assertIsDisplayed()
        }

    @Test
    fun `all frames with nothing to replace adds no note`() =
        scoped(burst()) { onNodeWithTag(NOTE_TAG).assertDoesNotExist() }

    @Test
    fun `the open frame's own edit is not counted`() =
        scoped(burst(overridden = setOf(2)), frameIndex = 2) {
            onNodeWithTag(NOTE_TAG).assertDoesNotExist()
        }

    @Test
    fun `choosing the frame takes the warning away`() =
        scoped(burst(overridden = setOf(1, 4)), frameIndex = 2) {
            onNodeWithText("Frame 3").performClick()
            waitForIdle()

            onNodeWithTag(NOTE_TAG).assertDoesNotExist()
        }

    /**
     * The line the whole strip exists for: `All Frames` clears every override, and
     * that is work the user may have done frames ago.
     */
    @Test
    fun `all frames states what it is about to replace`() =
        scoped(burst(overridden = setOf(1, 4)), frameIndex = 2) {
            onNodeWithTag(NOTE_TAG).assertIsDisplayed()
            onNodeWithText("Replaces the separate edits on 2 frames").assertIsDisplayed()
        }

    /** A regression guard for the `if` landing in the wrong branch of the `Box`. */
    @Test
    fun `a single picture still shows nothing once a tool is open`() =
        scoped(EditorFrames()) {
            onNodeWithTag("editor-tab-adjust").performClick()
            waitForIdle()

            onNodeWithTag(SCOPE_TAG).assertDoesNotExist()
        }
}
