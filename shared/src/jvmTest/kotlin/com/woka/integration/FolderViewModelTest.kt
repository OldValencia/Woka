package com.woka.integration

import com.woka.core.data.SortType
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
import kotlin.test.assertTrue

/** Listing, sorting and refresh. Multi-selection is `FolderSelectionTest`. */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `the listing arrives from disk`() {
        onFolder { viewModel, dir ->
            dir.media("b.jpg", 2_000_000_000_000L)
            dir.media("a.jpg", 1_000_000_000_000L)
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg", "b.jpg"))
            awaitTrue("loading to finish") { !viewModel.isLoading.value }
        }
    }

    @Test
    fun `a new sort reaches the state through the settings repository`() {
        onFolder { viewModel, dir ->
            dir.media("b.jpg", 1_000_000_000_000L)
            dir.media("a.jpg", 2_000_000_000_000L)
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg", "b.jpg"))

            viewModel.setSort(SortType.DateAsc)
            viewModel.awaitNames(listOf("b.jpg", "a.jpg"))
            assertEquals(SortType.DateAsc, viewModel.state.value.sort)

            viewModel.setSort(SortType.DateDesc)
            viewModel.awaitNames(listOf("a.jpg", "b.jpg"))
            assertEquals(SortType.DateDesc, viewModel.state.value.sort)
        }
    }

    @Test
    fun `the sort is remembered per folder`() {
        onFolder { viewModel, _ ->
            viewModel.setSort(SortType.NameDesc)
            awaitTrue("the sort to be stored") {
                viewModel.settingsRepository.settings.value.folderSorts[viewModel.path] == SortType.NameDesc
            }
        }
    }

    @Test
    fun `date order needs real timestamps, and holds folders on top`() {
        onFolder { viewModel, dir ->
            dir.media("newest.jpg", 3_000_000_000_000L)
            dir.media("oldest.jpg", 1_000_000_000_000L)
            File(dir, "Sub").mkdirs()
            viewModel.refresh()
            viewModel.setSort(SortType.DateAsc)
            viewModel.awaitNames(listOf("Sub", "oldest.jpg", "newest.jpg"))
            viewModel.setSort(SortType.DateDesc)
            viewModel.awaitNames(listOf("Sub", "newest.jpg", "oldest.jpg"))
        }
    }

    @Test
    fun `refresh picks up a file that appeared after the first listing`() {
        onFolder { viewModel, dir ->
            dir.media("a.jpg", 1_000_000_000_000L)
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg"))
            dir.media("b.jpg", 2_000_000_000_000L)
            viewModel.refresh()
            viewModel.awaitNames(listOf("a.jpg", "b.jpg"))
        }
    }

    @Test
    fun `pinning only accepts a real directory`() {
        onFolder { viewModel, dir ->
            viewModel.pinFolder(File(dir, "not-there").absolutePath)
            assertTrue(viewModel.settingsRepository.settings.value.pinnedSessions.isEmpty())
            viewModel.pinFolder(dir.absolutePath)
            awaitTrue("the folder to be pinned") {
                viewModel.settingsRepository.settings.value.pinnedSessions.size == 1
            }
        }
    }
}
