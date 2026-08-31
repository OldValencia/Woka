package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.app.shell.AppExitRequest
import com.woka.app.shell.EXIT_GRACE_MS
import com.woka.support.ExitScene
import com.woka.support.MainDispatcherRule
import com.woka.support.onUi
import com.woka.support.resetExitState
import com.woka.support.withEditorOpen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ASKED = "Save before quitting?"

/**
 * The question the close button asks over unsaved work (ME-160).
 *
 * Found by hand 2026-08-31: drawing a rectangle and pressing the window's close button
 * quit Woka with no question and lost the edit. The two paths that write — `Save` and
 * `Don't save` — are `EditorExitSaveUiTest`; what is here is the question itself, the
 * silence when there is nothing to ask about, and the retreat.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorExitUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun reset() = resetExitState()

    /** The ordinary case, and it must stay silent: nothing is at stake. */
    @Test
    fun `a clean editor quits with no question at all`() {
        var exits = 0
        withEditorOpen {
            onUi {
                setContent { ExitScene(isDirty = false, onSave = {}, onExit = { exits++ }) }
                waitForIdle()

                AppExitRequest.request()
                waitForIdle()
                onNodeWithText(ASKED).assertDoesNotExist()
                assertEquals(1, exits, "a clean editor held the window shut")
            }
        }
    }

    @Test
    fun `a dirty editor is asked, and an unanswered question never quits`() {
        var exits = 0
        withEditorOpen {
            onUi {
                setContent { ExitScene(isDirty = true, onSave = {}, onExit = { exits++ }) }
                AppExitRequest.request()
                waitForIdle()

                onNodeWithText(ASKED).assertIsDisplayed()
                assertEquals(0, exits, "the window went without waiting for an answer")

                // ME-125's cap bounds the *write*, never the question: a dialog left
                // standing must not quit the application five seconds later.
                mainClock.advanceTimeBy(EXIT_GRACE_MS + 100)
                waitForIdle()
                assertEquals(0, exits, "an unanswered question quit the application")
            }
        }
    }

    /**
     * The regression `AppExitRequest.cancel` exists for: a request left standing is a
     * `LaunchedEffect(requested)` that will not restart, and the second press of the
     * close button would do nothing with no way to find out why.
     */
    @Test
    fun `Cancel keeps the window, and a second press asks again`() {
        var exits = 0
        withEditorOpen {
            onUi {
                setContent { ExitScene(isDirty = true, onSave = {}, onExit = { exits++ }) }
                AppExitRequest.request()
                waitForIdle()

                onNodeWithText("Cancel").performClick()
                waitForIdle()
                assertEquals(0, exits, "Cancel quit the application")
                onNodeWithText(ASKED).assertDoesNotExist()

                AppExitRequest.request()
                waitForIdle()
                onNodeWithText(ASKED).assertIsDisplayed()
                assertEquals(0, exits)
            }
        }
    }
}
