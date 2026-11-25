package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.tools.crop.CROP_OVERLAY_TAG
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test

/** The right-hand inspector (RD-201): the merged Transform tab and the panel's toggle. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorInspectorPanelUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val tools = listOf(FixtureTool(id = "adjust", domain = ToolDomain.Color), TransformTool, CropTool)

    @Test
    fun `Transform shows the crop frame and both tools' rows`() {
        onEditor(tools) { harness ->
            onUi {
                setContent { EditorUnderShell(harness, TopBarAccessoriesState()) }
                onNodeWithTag(CROP_OVERLAY_TAG).assertDoesNotExist()

                onNodeWithTag("editor-tab-transform").performClick()
                waitForIdle()

                onNodeWithTag(CROP_OVERLAY_TAG).assertIsDisplayed()
                onAllNodesWithTag("editor-inspector").assertCountEquals(2)
                onNodeWithTag("param-aspect").assertIsDisplayed()
                onNodeWithTag("param-rotateLeft").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `the header's panel button hides the inspector and the canvas tool with it`() {
        onEditor(tools) { harness ->
            onUi {
                setContent { EditorUnderShell(harness, TopBarAccessoriesState()) }
                onNodeWithTag("editor-tab-transform").performClick()
                waitForIdle()

                onNodeWithTag("editor-panel-toggle").performClick()
                waitForIdle()
                onNodeWithTag("editor-tabs").assertIsNotDisplayed()
                onNodeWithTag(CROP_OVERLAY_TAG).assertDoesNotExist()

                onNodeWithTag("editor-panel-toggle").performClick()
                waitForIdle()
                onNodeWithTag("editor-tabs").assertIsDisplayed()
                onNodeWithTag(CROP_OVERLAY_TAG).assertIsDisplayed()
            }
        }
    }
}
