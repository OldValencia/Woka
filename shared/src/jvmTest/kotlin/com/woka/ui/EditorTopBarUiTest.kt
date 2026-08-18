package com.woka.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.app.shell.CanvasOverrideState
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What the editor does to the bar above it: which slots it claims, which it borrows,
 * and what the viewer underneath gets back on the way out.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorTopBarUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /**
     * The mark is drawn by `FileMetaBlock` in the Information panel — it left the top bar
     * when the file name gave that space back to the pill. Publishing the fact is the
     * shell's half, and that is what this holds.
     */
    @Test
    fun `the dirty flag reaches the shell only after a commit`() {
        val tool = FixtureTool(id = "adjust")
        onEditor(listOf(tool)) { harness ->
            onUi {
                val override = CanvasOverrideState()
                setContent { EditorUnderShell(harness, TopBarAccessoriesState(), override) }

                assertTrue(override.editing, "the pill was never told an editor is up")
                assertFalse(override.edited)

                harness.apply(ToolOp("adjust", tool.defaults()), ToolCardinality.Single)
                waitForIdle()

                assertTrue(override.edited)
            }
        }
    }

    @Test
    fun `Done lives in the shell's bar and fires once`() {
        var dones = 0
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent { EditorUnderShell(harness, TopBarAccessoriesState(), onDone = { dones++ }) }
                onNodeWithTag("editor-done").performClick()
            }
        }
        assertEquals(1, dones)
    }

    @Test
    fun `Back cancels the editor, and there is no second Cancel button`() {
        var cancels = 0
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                val accessories = TopBarAccessoriesState()
                setContent { EditorUnderShell(harness, accessories, onCancel = { cancels++ }) }
                waitForIdle()

                // The editor overrides Back rather than adding a control beside it —
                // one meaning, one button, as the viewer already does.
                onNodeWithTag("editor-cancel").assertDoesNotExist()
                accessories.backActionOverride?.invoke()
            }
        }
        assertEquals(1, cancels)
    }

    @Test
    fun `the viewer keeps its pill, and gets its Back and actions returned`() {
        val accessories = TopBarAccessoriesState()
        val viewersPill: @Composable () -> Unit = {}
        val viewersBack: () -> Unit = {}
        accessories.centerAccessory = viewersPill
        accessories.backActionOverride = viewersBack

        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent { EditorUnderShell(harness, accessories) }
                waitForIdle()

                // The centre is *not* claimed: replacing it would take the zoom control
                // away and leave the eye and Edit nothing to animate out of.
                assertSame(viewersPill, accessories.centerAccessory)
                assertTrue(accessories.backActionOverride !== viewersBack)
            }
        }

        // On the way out the viewer gets its Back back rather than one that exits the
        // folder — the effect that installed it will not run a second time.
        assertSame(viewersPill, accessories.centerAccessory)
        assertSame(viewersBack, accessories.backActionOverride)
    }

    @Test
    fun `opened with no viewer under it, the editor brings its own pill`() {
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                val accessories = TopBarAccessoriesState()
                setContent { EditorUnderShell(harness, accessories) }
                waitForIdle()

                // From the grid's context menu there is no pill to borrow, and a bar
                // showing only the folder path is what the review called useless.
                assertTrue(accessories.centerAccessory != null)
            }
        }
    }
}
