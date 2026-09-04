package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.hdr.HdrTool
import com.woka.support.FixtureEngine
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test

/**
 * The rail against the file, not against the medium (ME-302).
 *
 * `VideoRailToolsTest` covers the medium — `manifest.supports` — and this covers the
 * question the registry cannot answer: an SDR photograph offered the HDR tool, and
 * neither `Keep HDR` nor `Convert to SDR` could move anything on it.
 *
 * Driven through `EditorScreen` rather than through `toolsForPicture` directly, because
 * what is being proved is that the *shell* asks: the filter's own logic is
 * `ToolsForPictureTest`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorRailHdrUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** A second tool, so an empty rail cannot pass either of these by accident. */
    private val declared = listOf(HdrTool, FixtureTool(id = "adjust"))

    @Test
    fun `an SDR picture's rail has no HDR button`() {
        onEditor(declared) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tool-adjust").assertIsDisplayed()
                // Hidden, not struck through: `EditorRail`'s `unavailable` map says "this
                // tool cannot apply here", which is the wrong sentence for a control with
                // nothing to say about this file at all.
                onNodeWithTag("editor-tool-hdr").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `and a PQ picture's rail keeps it`() {
        val engine = FixtureEngine(sourceColor = StillColorInfo(TransferKind.PQ, isWideGamut = true))
        onEditor(declared) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, engine, harness.registry, {}, {})
                }
                onNodeWithTag("editor-tool-hdr").assertIsDisplayed()
            }
        }
    }
}
