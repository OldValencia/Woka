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
     * **The overlay has to follow what the engine drew** (2026-08-22).
     *
     * With a flip in the document the canvas draws the picture mirrored — source
     * `(0, y)` lands at the right-hand edge — while the stored rect stays in source
     * coordinates. An overlay that placed it through the canvas geometry alone put the
     * rectangle on the far side of the picture from the region it keeps, which by hand
     * looked like it flying off the picture entirely.
     *
     * Grabbing the **visual left** edge must therefore take hold of the rect's *right*
     * edge; before the fix it took the left one, and the rectangle grew away from the
     * cursor.
     */
    @Test
    fun `a mirrored preview mirrors where the handles are`() {
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

                // Just inside the picture's left edge on screen, which is the source's
                // right edge once the preview is mirrored.
                onNodeWithTag(CROP_OVERLAY_TAG).performMouseInput {
                    moveTo(centerLeft + Offset(3f, 0f))
                    press()
                    moveTo(centerLeft + Offset(80f, 0f))
                    release()
                }
                waitForIdle()

                val rect = harness.session.document.value.ops
                    .last().params[CropTool.Rect]
                assertEquals(0, rect.left, "the left edge moved — the grab was mirrored wrongly")
                assertTrue(rect.width < source.width, "nothing was resized at all")
            }
        }
    }
}
