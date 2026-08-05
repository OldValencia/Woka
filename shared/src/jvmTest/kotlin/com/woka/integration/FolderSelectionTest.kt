package com.woka.integration

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

@OptIn(ExperimentalCoroutinesApi::class)
class FolderSelectionTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `selecting an item turns select mode on`() {
        onFolder { viewModel, dir ->
            val file = dir.media("a.jpg")
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg"))
            assertFalse(viewModel.state.value.isSelectMode)
            viewModel.toggleSelection(file.absolutePath)
            awaitTrue("select mode to come on") { viewModel.state.value.isSelectMode }
            assertEquals(setOf(file.absolutePath), viewModel.state.value.selectedPaths)
        }
    }

    @Test
    fun `selecting the same item twice deselects it`() {
        onFolder { viewModel, dir ->
            val file = dir.media("a.jpg")
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg"))
            viewModel.toggleSelection(file.absolutePath)
            viewModel.toggleSelection(file.absolutePath)
            awaitTrue("the item to be deselected") { viewModel.state.value.selectedPaths.isEmpty() }
        }
    }

    @Test
    fun `leaving select mode clears the selection`() {
        onFolder { viewModel, dir ->
            val file = dir.media("a.jpg")
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg"))
            viewModel.toggleSelection(file.absolutePath)
            awaitTrue("the selection to register") { viewModel.state.value.selectedPaths.isNotEmpty() }
            viewModel.toggleSelectMode()
            awaitTrue("the selection to clear") {
                !viewModel.state.value.isSelectMode && viewModel.state.value.selectedPaths.isEmpty()
            }
        }
    }

    @Test
    fun `a range runs from the last item touched`() {
        onFolder { viewModel, dir ->
            val names = listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg")
            names.forEachIndexed { index, name -> dir.media(name, 1_000_000_000_000L + index) }
            viewModel.refresh()
            viewModel.awaitNames(names)

            viewModel.toggleSelection(File(dir, "b.jpg").absolutePath)
            viewModel.selectRange(File(dir, "d.jpg").absolutePath)
            awaitTrue("the range to be selected") { viewModel.state.value.selectedPaths.size == 3 }
            val selected = viewModel.state.value.selectedPaths.map { File(it).name }.toSet()
            assertEquals(setOf("b.jpg", "c.jpg", "d.jpg"), selected)
        }
    }

    @Test
    fun `a range runs backwards just as well`() {
        onFolder { viewModel, dir ->
            val names = listOf("a.jpg", "b.jpg", "c.jpg")
            names.forEachIndexed { index, name -> dir.media(name, 1_000_000_000_000L + index) }
            viewModel.refresh()
            viewModel.awaitNames(names)

            viewModel.toggleSelection(File(dir, "c.jpg").absolutePath)
            viewModel.selectRange(File(dir, "a.jpg").absolutePath)
            awaitTrue("the range to be selected") { viewModel.state.value.selectedPaths.size == 3 }
        }
    }

    @Test
    fun `a range with nothing selected yet just selects the one item`() {
        onFolder { viewModel, dir ->
            val file = dir.media("a.jpg")
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg"))
            viewModel.selectRange(file.absolutePath)
            awaitTrue("the single item to be selected") {
                viewModel.state.value.selectedPaths == setOf(file.absolutePath)
            }
        }
    }

    @Test
    fun `select all takes everything currently listed, and clear empties it`() {
        onFolder { viewModel, dir ->
            val names = listOf("a.jpg", "b.mkv", "c.png")
            names.forEach { dir.media(it) }
            viewModel.refresh()
            viewModel.awaitNames(names)
            viewModel.selectAll()
            awaitTrue("everything to be selected") { viewModel.state.value.selectedPaths.size == 3 }
            viewModel.clearSelection()
            awaitTrue("the selection to clear") { viewModel.state.value.selectedPaths.isEmpty() }
        }
    }
}
