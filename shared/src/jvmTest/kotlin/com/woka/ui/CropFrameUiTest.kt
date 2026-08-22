package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.support.FixtureEngine
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
 * A canvas tool must not be shown its own result (2026-08-22).
 *
 * The canvas measures its box with `engine.drawnSize(document)`, which **moves** the
 * moment a crop lands. While the crop tool was open, that moved size was also the frame
 * its own rectangle was clamped into: the frame shrank, the frame clamped the rect, the
 * rect shrank the frame again. By hand it looked like the rectangle collapsing on every
 * click and the picture refusing to be dragged near its own edges.
 *
 * **Asserted on what the canvas is handed, not on the document.** The first version of
 * this test watched the stored rect and passed with the bug still in place — a click
 * that writes nothing leaves the document alone whether or not the frame underneath it
 * is wrong. What actually distinguishes the two is the document the engine is asked to
 * draw, which is what `FixtureEngine.documentsDrawn` records.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CropFrameUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(640, 480)
    private val kept = CropRect(100, 80, 200, 150)

    /** An engine whose frame follows the crop, as a real image engine's does. */
    private fun croppingEngine() = FixtureEngine(
        sourceSize = source,
        drawnSizeOf = { document ->
            val rect = document.opsOf(CropTool.manifest.id).lastOrNull()?.params?.get(CropTool.Rect)
            if (rect == null || rect.width <= 0) source else IntSize(rect.width, rect.height)
        }
    )

    private fun documentWith(rect: CropRect) = EditDocument(
        ops = listOf(ToolOp(CropTool.manifest.id, CropTool.defaults().with(CropTool.Rect, rect)))
    )

    @Test
    fun `the canvas is drawn without the crop while the crop tool is open`() {
        val engine = croppingEngine()
        onEditor(listOf(CropTool), documentWith(kept)) { harness ->
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
                waitForIdle()
                // Closed, the crop is part of the picture like any other operation.
                assertTrue(
                    engine.documentsDrawn.last().opsOf(CropTool.manifest.id).isNotEmpty(),
                    "the crop was missing from the canvas before its tool was even open"
                )

                onNodeWithTag("editor-tool-crop").performClick()
                waitForIdle()

                // Open, it is the overlay's to show — and folding it in as well is what
                // made the rectangle measure itself.
                assertEquals(
                    emptyList(),
                    engine.documentsDrawn.last().opsOf(CropTool.manifest.id),
                    "the open crop tool was shown its own result"
                )
            }
        }
    }

    /** A tool that draws no overlay keeps its work on the canvas, open or not. */
    @Test
    fun `another tool's work stays on the canvas when it is open`() {
        val engine = croppingEngine()
        val document = documentWith(kept)
        onEditor(listOf(TransformTool, CropTool), document) { harness ->
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
                onNodeWithTag("editor-tool-transform").performClick()
                waitForIdle()

                assertTrue(
                    engine.documentsDrawn.last().opsOf(CropTool.manifest.id).isNotEmpty(),
                    "opening Transform hid the crop the picture already carried"
                )
            }
        }
    }
}
