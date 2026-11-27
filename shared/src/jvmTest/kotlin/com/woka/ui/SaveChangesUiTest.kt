package com.woka.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.EditorSaveStatus
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.tools.resize.SizeChoice
import com.woka.feature.editor.tools.resize.resizeOpFor
import com.woka.feature.editor.tools.resize.savedSize
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.WaysOutHost
import com.woka.support.sizeFields
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Save your changes?` (RD-401, `#dialogs/done`): a photo's `Done` asks its size and saves
 * it; a film's only confirms.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SaveChangesUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() = EditorSaveStatus.clear()

    private fun ComposeUiTest.selected(key: String) =
        onNodeWithTag("output-size").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, key))

    /** A 4000 × 3000 photo's host; [block] gets what each `Save` wrote. */
    private fun photo(block: ComposeUiTest.(() -> SizeChoice) -> Unit) = onEditor(emptyList()) { harness ->
        val engine = FixtureEngine(sourceSize = IntSize(4000, 3000))
        onUi {
            setContent { WaysOutHost(harness, engine) }
            block { engine.saveRequests.last().savedSize() }
        }
    }

    private fun ComposeUiTest.done() {
        onNodeWithTag("host-done").performClick()
        waitForIdle()
        onNodeWithText("Save your changes?").assertIsDisplayed()
    }

    private fun ComposeUiTest.save() {
        onNodeWithTag("editor-save-confirm").performClick()
        waitForIdle()
    }

    @Test
    fun `Done asks, opens at Original, and a preset saves its proportion`() = photo { saved ->
        done()
        selected("100")
        sizeFields("4000", "3000")

        onNodeWithText("50%").performClick()
        sizeFields("2000", "1500")
        save()
        onNodeWithText("Save your changes?").assertDoesNotExist()
        assertEquals(SizeChoice.Percent(50f), saved())

        // Reopened at what was saved.
        done()
        selected("50")
        sizeFields("2000", "1500")
    }

    @Test
    fun `typing selects Custom, linked and then independent`() = photo { saved ->
        done()
        onNodeWithTag("output-width").performTextReplacement("1000")
        selected("custom")
        sizeFields("1000", "750")
        save()
        assertEquals(SizeChoice.Custom(1000, 750, keepProportions = true), saved())

        done()
        selected("custom")
        onNodeWithTag("output-keep-proportions").performClick()
        onNodeWithTag("output-height").performTextReplacement("100")
        sizeFields("1000", "100")
        save()
        assertEquals(SizeChoice.Custom(1000, 100, keepProportions = false), saved())
    }

    @Test
    fun `Original takes a saved Resize away`() = photo { saved ->
        done()
        onNodeWithText("25%").performClick()
        save()
        assertEquals(SizeChoice.Percent(25f), saved())

        done()
        onNodeWithText("Original").performClick()
        save()
        assertEquals(SizeChoice.Original, saved())
    }

    /** The old slider's 37 % opens as pixels, and an untouched `Save` keeps the 37 %. */
    @Test
    fun `an existing percentage outside the presets is kept as it was`() {
        val legacy = EditDocument(ops = listOfNotNull(resizeOpFor(SizeChoice.Percent(37f))))
        onEditor(emptyList(), initial = legacy) { harness ->
            val engine = FixtureEngine(sourceSize = IntSize(4000, 3000))
            onUi {
                setContent { WaysOutHost(harness, engine) }
                done()
                selected("custom")
                sizeFields("1480", "1110")
                save()
                assertEquals(SizeChoice.Percent(37f), engine.saveRequests.last().savedSize())
            }
        }
    }

    @Test
    fun `a film is only asked to confirm`() = onEditor(emptyList()) { harness ->
        val engine = FixtureEngine(kind = MediaKind.Video)
        onUi {
            setContent { WaysOutHost(harness, engine) }
            done()
            onNodeWithText("Keep these edits so you can come back to them in Woka.").assertIsDisplayed()
            onNodeWithTag("output-size").assertDoesNotExist()
            save()
            assertEquals(1, engine.saveRequests.size)
        }
    }
}
