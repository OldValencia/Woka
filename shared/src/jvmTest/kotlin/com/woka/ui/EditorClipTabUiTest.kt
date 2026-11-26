package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.pickClip
import com.woka.feature.editor.shell.CanvasKeys
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.support.EditorHarness
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onFilm
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WHOLE_TAG = "editor-scope-whole"
private const val CLIP_TAB = "editor-tab-trim"

/**
 * The film's selection and its tab (RD-302, `#editor/video`): Clip is a tab only while a clip
 * is selected; Esc and `Whole video` take the selection back, and leave Clip for the first tab.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorClipTabUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)

    private val keys = CanvasKeys()

    private fun film(cut: Boolean, selected: String?, block: ComposeUiTest.(EditorHarness) -> Unit) =
        onFilm(listOf(TrimTool, tool), cut, selected, keys, block)

    @Test
    fun `Clip's tab is there only while a clip is selected, and first`() =
        film(cut = true, selected = null) { harness ->
            onNodeWithTag(CLIP_TAB).assertDoesNotExist()
            runOnIdle { harness.session.pickClip("c2", 1) }
            waitForIdle()
            onNodeWithTag(CLIP_TAB).assertIsDisplayed()
        }

    @Test
    fun `Esc drops the clip before it closes anything, then hands on`() =
        film(cut = true, selected = "c2") { harness ->
            assertTrue(runOnIdle { keys.onBack!!.invoke() }, "Esc did not take the clip back")
            waitForIdle()
            assertNull(harness.session.clipSelection.value)
            onNodeWithTag(CLIP_TAB).assertDoesNotExist()
            assertFalse(runOnIdle { keys.onBack!!.invoke() }, "with nothing selected Esc must close the editor")
        }

    @Test
    fun `whole video from Clip opens the first tab`() =
        film(cut = true, selected = "c2") { harness ->
            onNodeWithTag(CLIP_TAB).performClick()
            waitForIdle()
            onNodeWithTag(CLIP_TAB).assertIsSelected()
            onNodeWithTag(WHOLE_TAG).performClick()
            waitForIdle()
            onNodeWithTag(CLIP_TAB).assertDoesNotExist()
            onNodeWithTag("editor-tab-adjust").assertIsSelected()
            assertEquals(null, harness.session.clipSelection.value)
        }
}
