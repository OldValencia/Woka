package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.HdrView
import com.woka.core.data.HdrViewMode
import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.feature.editor.EditorRenderStatus
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.tools.resize.SizeChoice
import com.woka.feature.editor.tools.resize.resizeOpFor
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.copying
import com.woka.support.createCopy
import com.woka.support.sizeFields
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `Create a finished copy` (RD-401, `#dialogs/copy`): the copy's size, the colour mode's note,
 * `Audio only…` closed — and the saved Resize left as it was.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CreateCopyUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() {
        HdrView.install { HdrViewMode.Auto }
        EditorRenderStatus.clear()
    }

    @Test
    fun `a photo's percentage sizes the copy and leaves the saved Resize alone`() {
        val saved = EditDocument(ops = listOfNotNull(resizeOpFor(SizeChoice.Percent(50f))))
        copying(FixtureEngine(sourceSize = IntSize(4000, 3000)), saved) { engine ->
            // A copy opens at Original whatever `Done` saved.
            sizeFields("4000", "3000")
            onNodeWithText("Audio only…").assertDoesNotExist()
            onNodeWithTag("editor-copy-retone").assertDoesNotExist()
            onNodeWithText("25%").performClick()
            sizeFields("1000", "750")
            createCopy()
            assertEquals(IntSize(1000, 750), engine.exported.single().size)
        }
    }

    @Test
    fun `a portrait photo keeps its orientation, and Original sends no size`() =
        copying(FixtureEngine(sourceSize = IntSize(3000, 4000))) { engine ->
            onNodeWithText("50%").performClick()
            sizeFields("1500", "2000")
            onNodeWithText("Original").performClick()
            createCopy()
            assertNull(engine.exported.single().size)
        }

    @Test
    fun `custom pixels may change the shape, and refused ones disable the copy`() =
        copying(FixtureEngine(sourceSize = IntSize(4000, 3000))) { engine ->
            onNodeWithTag("output-width").performTextReplacement("20000")
            onNodeWithTag("output-size-error").assertIsDisplayed()
            onNodeWithTag("editor-copy-confirm").assertIsNotEnabled()

            onNodeWithTag("output-keep-proportions").performClick()
            onNodeWithTag("output-width").performTextReplacement("500")
            onNodeWithTag("output-height").performTextReplacement("500")
            onNodeWithTag("output-size-error").assertDoesNotExist()
            createCopy()
            assertEquals(IntSize(500, 500), engine.exported.single().size)
        }

    /** No second 720p beside a 720p Original; what the button shows is what is exported. */

    /** The copy follows the screen (2026-11-27), and says so when that retones the picture. */

    @Test
    fun `the colour mode's note shows only when it changes the picture`() {
        HdrView.install { HdrViewMode.ForceSdr }
        copying(FixtureEngine(sourceColor = StillColorInfo(TransferKind.PQ, isWideGamut = true))) { _ ->
            onNodeWithTag("editor-copy-retone").assertIsDisplayed()
            onNodeWithText("Colour is set to SDR on screen", substring = true).assertIsDisplayed()
        }
        copying(FixtureEngine()) { _ ->
            onNodeWithTag("editor-copy-retone").assertDoesNotExist()
        }
    }
}
