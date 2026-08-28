package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.filter.FilterTool
import com.woka.feature.editor.tools.filter.PhotoFilter
import com.woka.feature.editor.tools.filter.PhotoUndertone
import com.woka.support.FixtureEngine
import com.woka.support.FixtureParams
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The preview-choice strip (ME-112) — the row ME-102 deferred until there was something
 * to preview.
 *
 * **The shell never draws a picture.** It asks the engine for one per option, handing
 * over a document holding only that option's operation; the fixture engine records what
 * it was asked for, which is how "one thumbnail per option, and no more" is asserted at
 * all.
 *
 * What clicking one *does* is [PreviewChoiceWriteUiTest].
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class PreviewChoiceRowUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `the strip draws one thumbnail per option`() {
        val engine = FixtureEngine()
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = engine,
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-filter").performClick()
                waitForIdle()

                // Unmerged: each option's card is `clickable`, which merges its
                // descendants' semantics — the thumbnails inside stop being nodes of
                // their own in the merged tree.
                //
                // **Two strips since ME-141**: Styles draws an undertone above the
                // look, and they are independent selections rather than one list.
                onAllNodesWithTag(FixtureEngine.THUMBNAIL_TAG, useUnmergedTree = true)
                    .assertCountEquals(PhotoFilter.entries.size + PhotoUndertone.entries.size)
            }
        }
    }

    /**
     * **Each thumbnail shows its own look**, not the edit as a whole — otherwise every
     * option in the strip would look like the canvas behind it.
     */
    @Test
    fun `each thumbnail is asked for with its own option`() {
        val engine = FixtureEngine()
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = engine,
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-filter").performClick()
                waitForIdle()

                val asked = engine.thumbnailsDrawn
                    .mapNotNull { it.ops.singleOrNull()?.params?.get(FilterTool.Filter) }
                    .toSet()
                assertEquals(PhotoFilter.entries.toSet(), asked, "the strip previewed the same look twice")
                assertTrue(
                    engine.thumbnailsDrawn.all { it.ops.size == 1 },
                    "a thumbnail carried more than its own operation"
                )
            }
        }
    }

    /**
     * **One thumbnail per option, and no more.** The strip draws the same bitmap through
     * eight colour filters, which is a filter rebuild each and no pixel work — the
     * property §3.2 was designed for, and the one that would regress in silence, because
     * a strip that decodes eight times per recomposition looks exactly the same.
     */
    @Test
    fun `the strip asks for one thumbnail per option and no more`() {
        val engine = FixtureEngine()
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = engine,
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-filter").performClick()
                waitForIdle()

                val expected = PhotoFilter.entries.size + PhotoUndertone.entries.size
                assertEquals(
                    expected,
                    engine.thumbnailsDrawn.size,
                    "the two strips drew ${engine.thumbnailsDrawn.size} thumbnails for $expected options"
                )
            }
        }
    }

    /**
     * The heading ME-141 added, and the reason it is optional: two adjacent strips
     * with no labels read as one strip that wrapped, while every other tool in the
     * app has a single strip that needs no heading at all.
     */
    @Test
    fun `a strip with a section draws its heading and one without draws none`() {
        val engine = FixtureEngine()
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = engine,
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-filter").performClick()
                waitForIdle()

                onNodeWithTag("param-undertone-section").assertExists()
                onNodeWithTag("param-filter-section").assertExists()
                // The fixture's own preview spec declares no section, and its row is
                // the one that must be laid out exactly as it was before.
                onNodeWithTag("param-${FixtureParams.Preview.key}-section").assertDoesNotExist()
            }
        }
    }

    /**
     * The strength lives **inside the chosen square** (asked for 2026-08-28), and is
     * absent on the option that is the absence of the effect — scaling `Original` by
     * 40 % scales nothing, which is the row `rows()` exists to keep off a panel.
     */
    @Test
    fun `the strength appears only on a chosen option that does something`() {
        val engine = FixtureEngine()
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = engine,
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-filter").performClick()
                waitForIdle()

                // Unmerged throughout: an option's card is `clickable`, which merges
                // its descendants, so the slider inside one is not a node of its own
                // in the merged tree.
                //
                // Opened on the defaults: Original and Neutral are selected, and
                // neither carries a strength.
                onNodeWithTag("param-intensity", useUnmergedTree = true).assertDoesNotExist()
                onNodeWithTag("param-palette", useUnmergedTree = true).assertDoesNotExist()

                onNodeWithTag("param-filter-${PhotoFilter.Vivid.name}")
                    .performScrollTo()
                    .performClick()
                waitForIdle()
                onNodeWithTag("param-intensity", useUnmergedTree = true).assertExists()
                // The undertone is still Neutral, so only one of the two appeared.
                onNodeWithTag("param-palette", useUnmergedTree = true).assertDoesNotExist()

                onNodeWithTag("param-undertone-${PhotoUndertone.Gold.name}")
                    .performScrollTo()
                    .performClick()
                waitForIdle()
                onNodeWithTag("param-palette", useUnmergedTree = true).assertExists()
            }
        }
    }
}
