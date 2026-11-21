package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.crop.CROP_OVERLAY_TAG
import com.woka.feature.editor.tools.crop.CropTool
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
 * The overlay follows what the engine actually drew (2026-08-22).
 *
 * Split from `CropFrameUiTest` under the size mandate: that file is about which
 * document the canvas is handed, and this is about where the rectangle lands on it.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CropMirrorUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(640, 480)

    /**
     * **The overlay has to follow what the engine drew** (2026-08-22, revised 2026-11-20).
     *
     * The fold applies a crop *after* the geometry before it (`RasterFold`), so its rect is
     * a rect of the **drawn frame** — the mirrored picture, here — and the overlay places it
     * without turning it through that geometry again (`CanvasTool.worksInFrame`). This
     * test first asserted the opposite, a rect in source pixels mirrored onto the canvas;
     * that disagreed with the fold, and after Rotate right it put the rect beside the
     * picture (reported 2026-11-20).
     *
     * Grabbing the **visual left** edge therefore takes hold of the rect's *left* edge, and
     * the right one stays where it was.
     */
    @Test
    fun `a mirrored preview keeps the handles where the frame shows them`() {
        val mirrored = FixtureEngine(
            sourceSize = source,
            previewMatrixOf = { Matrix().apply { translate(source.width.toFloat(), 0f); scale(-1f, 1f) } }
        )
        onEditor(listOf(CropTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = mirrored,
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-crop").performClick()
                waitForIdle()

                // Just inside the picture's left edge on screen, which is the frame's
                // left edge too.
                onNodeWithTag(CROP_OVERLAY_TAG).performMouseInput {
                    moveTo(centerLeft + Offset(3f, 0f))
                    press()
                    moveTo(centerLeft + Offset(80f, 0f))
                    release()
                }
                waitForIdle()

                val rect = harness.session.document.value.ops
                    .last().params[CropTool.Rect]
                assertEquals(source.width, rect.left + rect.width, "the right edge moved — the grab was mirrored")
                assertTrue(rect.left > 0, "the left edge did not follow the cursor")
            }
        }
    }

    /**
     * The report itself (2026-11-20): Rotate right, then Crop. The canvas draws the turned
     * picture in a 480×640 frame; turning the rect through the quarter turn again put it
     * beside the picture, and the visual left edge took hold of its top or bottom instead.
     */
    @Test
    fun `after a quarter turn the visual left edge is the rect's left edge`() {
        val turned = IntSize(source.height, source.width)
        val quarter = FixtureEngine(
            sourceSize = source,
            drawnSizeOf = { turned },
            previewMatrixOf = { Matrix().apply { translate(source.height.toFloat(), 0f); rotateZ(90f) } }
        )
        onEditor(listOf(CropTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = quarter,
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-crop").performClick()
                waitForIdle()

                onNodeWithTag(CROP_OVERLAY_TAG).performMouseInput {
                    moveTo(centerLeft + Offset(3f, 0f))
                    press()
                    moveTo(centerLeft + Offset(80f, 0f))
                    release()
                }
                waitForIdle()

                val rect = harness.session.document.value.ops
                    .last().params[CropTool.Rect]
                assertTrue(rect.left > 0, "the left edge did not follow the cursor")
                assertEquals(turned.width, rect.left + rect.width, "the right edge moved")
                assertEquals(turned.height, rect.height, "the grab changed the height — it was turned")
            }
        }
    }
}
