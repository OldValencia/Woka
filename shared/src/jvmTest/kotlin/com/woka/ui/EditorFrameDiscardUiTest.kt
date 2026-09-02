package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.shell.EditorHostDialogs
import com.woka.support.MainDispatcherRule
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What happens to unsaved work when the editor is closed.
 *
 * **It asked about a frame change too until ME-169**, and the file is named for that:
 * ME-158's answer was the discard dialog reworded, because the host rebuilt the session
 * on every frame and the work genuinely went with it. Frames carry their own document
 * and history now, so the question has one cause again — and this is the only file that
 * covers the dialog at all, which is why it was retargeted rather than deleted.
 *
 * Driven against `EditorHostDialogs` rather than `EditorHost`: the host resolves an
 * engine factory out of Koin and builds a real session, and none of that is the point.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorFrameDiscardUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())


    /**
     * The dialog the **close** raises, which is the one cause left (ME-169).
     *
     * It had a second: ME-158 asked the same question on a frame change, because the
     * host rebuilt the session and the work went with it. Frames keep their own document
     * and history now, so leaving one costs nothing and there is nothing to ask.
     */
    @Test
    fun `closing with dirty work asks before it is thrown away`() = onUi {
        var discarded = 0
        setContent {
            EditorHostDialogs(
                askingToRevert = false,
                revertScope = EditScope.AllFrames,
                onRevert = {},
                askingToDiscard = true,
                onDiscard = { discarded++ },
                onKeepEditing = {}
            )
        }
        waitForIdle()
        onNodeWithText("Discard changes?").assertExists()
        onNodeWithText("Discard").performClick()
        assertEquals(1, discarded)
    }

    /** A clean document never sees this at all — the ordinary case, and it is silent. */
    @Test
    fun `nothing pending draws no dialog`() = onUi {
        setContent {
            EditorHostDialogs(
                askingToRevert = false,
                revertScope = EditScope.AllFrames,
                onRevert = {},
                askingToDiscard = false,
                onDiscard = {},
                onKeepEditing = {}
            )
        }
        waitForIdle()
        onNodeWithText("Discard changes?").assertDoesNotExist()
    }
}
