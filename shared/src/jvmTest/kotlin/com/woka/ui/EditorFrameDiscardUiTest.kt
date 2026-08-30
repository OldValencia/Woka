package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.shell.EditorHostDialogs
import com.woka.support.MainDispatcherRule
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What happens to unsaved work when the edited frame changes (ME-158).
 *
 * The chosen answer is the discard dialog `Cancel` already uses, reworded to name what
 * is being left — so what is worth asserting is that the second cause reaches the same
 * component, says the right sentence, and moves the frame only on `Discard`.
 *
 * Driven against `EditorHostDialogs` rather than `EditorHost`: the host resolves an
 * engine factory out of Koin and builds a real session per frame, and none of that is
 * what this ticket decided.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorFrameDiscardUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val movingOn = "This edit has not been saved. Moving to another frame now loses it"

    @Test
    fun `a frame reached with dirty work asks, and names the frame rather than the exit`() {
        var moved = 0
        onUi {
            setContent {
                EditorHostDialogs(
                    askingToRevert = false,
                    onRevert = {},
                    askingToDiscard = false,
                    onDiscard = {},
                    pendingFrame = 3,
                    onChangeFrame = { moved++ },
                    onKeepEditing = {}
                )
            }
            waitForIdle()
            onNodeWithText(movingOn, substring = true).assertIsDisplayed()

            onNodeWithText("Discard").performClick()
        }
        assertEquals(1, moved, "confirming did not move to the frame")
    }

    @Test
    fun `keeping the edit leaves the frame where it was`() {
        var moved = 0
        var kept = 0
        onUi {
            setContent {
                EditorHostDialogs(
                    askingToRevert = false,
                    onRevert = {},
                    askingToDiscard = false,
                    onDiscard = {},
                    pendingFrame = 3,
                    onChangeFrame = { moved++ },
                    onKeepEditing = { kept++ }
                )
            }
            waitForIdle()

            onNodeWithText("Keep editing").performClick()
        }
        assertEquals(0, moved, "the frame changed anyway")
        assertEquals(1, kept)
    }

    /** A clean document never sees this at all — the ordinary case, and it is silent. */
    @Test
    fun `nothing pending draws no dialog`() = onUi {
        setContent {
            EditorHostDialogs(
                askingToRevert = false,
                onRevert = {},
                askingToDiscard = false,
                onDiscard = {},
                pendingFrame = null,
                onChangeFrame = {},
                onKeepEditing = {}
            )
        }
        waitForIdle()
        onNodeWithText("Discard changes?").assertDoesNotExist()
    }
}
