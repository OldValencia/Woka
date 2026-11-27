package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.core.ui.components.TOAST_UNDO_MS
import com.woka.feature.editor.EditorUndoBanner
import com.woka.feature.editor.EditorUndoToast
import com.woka.support.MainDispatcherRule
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val REMOVED = "Clip removed. The remaining clips close the gap."

/**
 * The editor's Undo toast (RD-401, `app.js` `toast(message, true)`): `Undo` takes the step
 * back, and the toast waits while the pointer is on it.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ToastUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() = EditorUndoToast.dismiss()

    @Test
    fun `Undo undoes, and the toast goes with it`() = onUi {
        var undone = 0
        setContent { EditorUndoBanner() }
        EditorUndoToast.offer(REMOVED) { undone++ }
        waitForIdle()

        onNodeWithText(REMOVED).assertIsDisplayed()
        onNodeWithTag("editor-undo-toast-undo").performClick()
        waitForIdle()

        assertEquals(1, undone)
        onNodeWithText(REMOVED).assertDoesNotExist()
    }

    @Test
    fun `it leaves on its own after seven seconds`() = onUi {
        setContent { EditorUndoBanner() }
        EditorUndoToast.offer(REMOVED) {}
        waitForIdle()

        mainClock.advanceTimeBy(TOAST_UNDO_MS - 500)
        onNodeWithText(REMOVED).assertIsDisplayed()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithText(REMOVED).assertDoesNotExist()
    }

    /** Hover pauses it; it leaves three seconds after the pointer does (`experience.js`). */
    @Test
    fun `the timer pauses while the pointer is on it`() = onUi {
        setContent { EditorUndoBanner() }
        EditorUndoToast.offer(REMOVED) {}
        waitForIdle()

        onNodeWithTag("editor-undo-toast").performMouseInput { enter(center) }
        mainClock.advanceTimeBy(TOAST_UNDO_MS * 2)
        waitForIdle()
        onNodeWithText(REMOVED).assertIsDisplayed()

        onNodeWithTag("editor-undo-toast").performMouseInput { exit() }
        mainClock.advanceTimeBy(2_500)
        onNodeWithText(REMOVED).assertIsDisplayed()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithText(REMOVED).assertDoesNotExist()
    }
}
