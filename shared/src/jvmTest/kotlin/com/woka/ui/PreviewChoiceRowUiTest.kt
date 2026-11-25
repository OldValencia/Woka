package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.shell.PAGE
import com.woka.feature.editor.tools.filter.FilterTool
import com.woka.feature.editor.tools.filter.PhotoFilter
import com.woka.support.EditorHarness
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Styles' rows of tiles (RD-203): four at a time between ← and →, each the picture through
 * its own option, a hover previewing it on the canvas. What a click writes is
 * [PreviewChoiceWriteUiTest].
 *
 * **The shell never draws a picture.** It asks the engine for one per shown option, handing
 * over a document holding only that option's operation; the fixture engine records it.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class PreviewChoiceRowUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun ComposeUiTest.openStyles(harness: EditorHarness, engine: FixtureEngine) {
        setContent { EditorScreen(harness.session, engine, harness.registry, {}, {}) }
        onNodeWithTag("editor-tab-filter").performClick()
        waitForIdle()
    }

    @Test
    fun `each row shows four tiles, each through its own option`() {
        val engine = FixtureEngine()
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                openStyles(harness, engine)

                // Unmerged: a tile is `clickable`, which merges what is inside it.
                onAllNodesWithTag(FixtureEngine.THUMBNAIL_TAG, useUnmergedTree = true).assertCountEquals(PAGE * 2)
                val looks = engine.thumbnailsDrawn.mapNotNull { it.ops.singleOrNull()?.params?.get(FilterTool.Filter) }.toSet()
                assertEquals(PhotoFilter.entries.take(PAGE).toSet(), looks)
                assertTrue(engine.thumbnailsDrawn.all { it.ops.size == 1 }, "a tile carried more than its own operation")
            }
        }
    }

    @Test
    fun `the arrow brings the next four, and the first page has no way back`() {
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                openStyles(harness, FixtureEngine())
                onNodeWithTag("param-filter-back").assertIsNotEnabled()

                onNodeWithTag("param-filter-next").performClick()
                waitForIdle()

                onNodeWithTag("param-filter-${PhotoFilter.Noir.name}").assertIsDisplayed()
                onNodeWithTag("param-filter-${PhotoFilter.Original.name}").assertDoesNotExist()
                onNodeWithTag("param-filter-next").assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `both rows are headed by their names`() {
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                openStyles(harness, FixtureEngine())
                onNodeWithTag("param-undertone-section").assertExists()
                onNodeWithTag("param-filter-section").assertExists()
            }
        }
    }

    /** Looking costs no history: the hover writes unsettled, leaving takes it back. */
    @Test
    fun `hovering a tile previews it, and leaving takes it back`() {
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                openStyles(harness, FixtureEngine())
                onNodeWithTag("param-filter-${PhotoFilter.Vivid.name}").performMouseInput { moveTo(center) }
                waitForIdle()
                assertNotNull(harness.session.document.value.pending, "a hover previewed nothing")

                onNodeWithTag("param-filter-${PhotoFilter.Vivid.name}").performMouseInput { moveTo(topLeft.copy(x = -50f)) }
                waitForIdle()
                assertNull(harness.session.document.value.pending, "the preview outlived the hover")
                assertTrue(harness.session.document.value.ops.isEmpty(), "a hover wrote an operation")
            }
        }
    }
}
