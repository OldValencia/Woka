package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.crop.CROP_OVERLAY_TAG
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
import kotlin.test.assertNull

/**
 * Leaving a canvas tool is not cancelling it (2026-08-22).
 *
 * Split from `CropFrameUiTest` under the size mandate.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CropToolSwitchUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /**
     * **Switching tools keeps the crop.** Reported as "the crop rolls back if you move
     * to Transform without pressing Done" (2026-08-22) — which turned out to be the
     * unclipped canvas rather than a lost operation, but the claim is worth holding
     * directly: leaving the tool is not cancelling it.
     */
    @Test
    fun `moving to another tool keeps the crop that was dragged`() {
        onEditor(listOf(TransformTool, CropTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = FixtureEngine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-crop").performClick()
                waitForIdle()

                onNodeWithTag(CROP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(2f, 2f))
                    press()
                    moveTo(Offset(60f, 50f))
                    release()
                }
                waitForIdle()
                val cropped = harness.session.document.value.ops
                    .last { it.toolId == CropTool.manifest.id }.params[CropTool.Rect]

                onNodeWithTag("editor-tool-transform").performClick()
                waitForIdle()

                assertEquals(
                    cropped,
                    harness.session.document.value.ops
                        .last { it.toolId == CropTool.manifest.id }.params[CropTool.Rect],
                    "switching tools changed the crop"
                )
                assertNull(harness.session.document.value.pending, "work was left in flight")
            }
        }
    }
}
