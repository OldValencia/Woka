package com.woka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test

private const val SCOPE_TAG = "editor-frame-scope"

/**
 * The switch that says who the edit is for (ME-156), at the level the user meets it.
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

    @Test
    fun `a burst gets the switch, and it says how many frames it covers`() =
        scoped(burst()) {
            onNodeWithTag(SCOPE_TAG).assertIsDisplayed()
            onNodeWithText("Applies to all 5 frames").assertIsDisplayed()
        }

    @Test
    fun `choosing this frame names the frame it is about`() =
        scoped(burst(), frameIndex = 2) {
            onNodeWithText("Single Frame").performClick()
            waitForIdle()

            onNodeWithText("Applies to frame 3 only").assertIsDisplayed()
        }

    /**
     * The line the whole strip exists for: `All Frames` clears every override, and
     * that is work the user may have done frames ago.
     */
    @Test
    fun `all frames states what it is about to replace`() =
        scoped(burst(overridden = setOf(1, 4)), frameIndex = 2) {
            onNodeWithText("Replaces the separate edits on 2 frames").assertIsDisplayed()
        }

    /** A regression guard for the `if` landing in the wrong branch of the `Box`. */
    @Test
    fun `a single picture still shows nothing once a tool is open`() =
        scoped(EditorFrames()) {
            onNodeWithTag("editor-tool-adjust").performClick()
            waitForIdle()

            onNodeWithTag(SCOPE_TAG).assertDoesNotExist()
        }
}
