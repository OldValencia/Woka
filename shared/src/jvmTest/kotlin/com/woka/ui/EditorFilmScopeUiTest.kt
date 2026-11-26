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
import com.woka.feature.editor.core.ToolDomain
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
import kotlin.test.assertNull

private const val SCOPE_TAG = "editor-frame-scope"
private const val WHOLE_TAG = "editor-scope-whole"
private const val CLIP_TAG = "editor-scope-clip"

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
        onFilm(listOf(tool), cut, selected, block = block)

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
