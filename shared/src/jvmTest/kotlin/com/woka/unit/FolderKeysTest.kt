package com.woka.unit

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.woka.feature.folder.FolderFocus
import com.woka.feature.folder.FolderKeyContext
import com.woka.feature.folder.FolderViewModel
import com.woka.feature.folder.ItemBox
import com.woka.feature.folder.folderKey
import com.woka.feature.folder.parentWithinRoot
import com.woka.feature.folder.stepAcross
import com.woka.feature.folder.stepVertically
import com.woka.support.MainDispatcherRule
import com.woka.support.awaitNames
import com.woka.support.awaitTrue
import com.woka.support.media
import com.woka.support.onFolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The grid's arrows and Backspace (SIDE-005), as the pure decisions they are: where
 * the focus goes from item positions, and where "up" is from a path.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class FolderKeysTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** Three tiles a row, 100 wide, rows 100 apart. */
    private val grid = (0 until 9).map { ItemBox(it, x = (it % 3) * 100, y = (it / 3) * 100, width = 100) }

    @Test
    fun `down lands on the item below`() {
        assertEquals(4, stepVertically(1, grid, down = true, count = 9))
        assertEquals(1, stepVertically(4, grid, down = false, count = 9))
    }

    @Test
    fun `down from a wide folder card lands on the tile nearest its centre`() {
        val boxes = listOf(
            ItemBox(0, x = 0, y = 0, width = 300),
            ItemBox(1, x = 0, y = 200, width = 100),
            ItemBox(2, x = 100, y = 200, width = 100),
            ItemBox(3, x = 200, y = 200, width = 100)
        )
        assertEquals(2, stepVertically(0, boxes, down = true, count = 4))
    }

    @Test
    fun `with the next row off screen the move is the current row's length`() {
        val visible = grid.take(3)
        assertEquals(4, stepVertically(1, visible, down = true, count = 9))
    }

    @Test
    fun `moves are held at both ends`() {
        assertEquals(8, stepVertically(7, grid, down = true, count = 9))
        assertEquals(0, stepAcross(0, right = false, count = 9))
        assertEquals(8, stepAcross(8, right = true, count = 9))
        assertEquals(3, stepAcross(2, right = true, count = 9))
    }

    @Test
    fun `backspace climbs inside a root and stops at it`() {
        val roots = listOf("D:/Media/Movies")
        val parent = parentWithinRoot("D:/Media/Movies/Series/Andor", roots)
        assertEquals(File("D:/Media/Movies/Series").absoluteFile.normalize().path, parent)
        assertNull(parentWithinRoot("D:/Media/Movies", roots))
        assertNull(parentWithinRoot("E:/Elsewhere/Folder", roots))
    }

    private fun withEscapeKey(block: (KeyEvent) -> Unit) = runComposeUiTest {
        val focus = FocusRequester()
        var event: KeyEvent? = null
        setContent {
            Box(Modifier.size(80.dp).testTag("k").focusRequester(focus).focusable().onKeyEvent {
                if (it.type == KeyEventType.KeyDown) event = it
                true
            })
        }
        focus.requestFocus()
        onNodeWithTag("k").performKeyInput { pressKey(Key.Escape) }
        block(event ?: error("No escape key event"))
    }

    private fun context(vm: FolderViewModel, focus: FolderFocus, path: String) = FolderKeyContext(
        vm.state.value, focus, LazyGridState(), vm.scope, vm, listOf(path), path, {}, {}
    )

    @Test
    fun `escape dismisses quick look then clears focused element`() = withEscapeKey { escape ->
        onFolder { viewModel, dir ->
            val file = dir.media("a.jpg")
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg"))
            val focus = FolderFocus()
            focus.focused = file.absolutePath
            focus.quickLook = true
            val ctx = context(viewModel, focus, dir.absolutePath)
            assertTrue(folderKey(escape, ctx))
            assertFalse(focus.quickLook)
            assertEquals(file.absolutePath, focus.focused)
            assertTrue(folderKey(escape, ctx))
            assertNull(focus.focused)
        }
    }

    @Test
    fun `escape clears selection and leaves select mode`() = withEscapeKey { escape ->
        onFolder { viewModel, dir ->
            val file = dir.media("a.jpg")
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg"))
            viewModel.toggleSelection(file.absolutePath)
            awaitTrue("select mode to come on") { viewModel.state.value.isSelectMode }
            val focus = FolderFocus()
            focus.focused = file.absolutePath
            assertTrue(folderKey(escape, context(viewModel, focus, dir.absolutePath)))
            awaitTrue("select mode to go off") { !viewModel.state.value.isSelectMode }
            assertNull(focus.focused)
        }
    }
}
