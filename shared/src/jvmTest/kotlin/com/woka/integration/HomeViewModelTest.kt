package com.woka.integration

import com.woka.core.data.PinnedSession
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.data.WokaStorage
import com.woka.feature.home.HomeViewModel
import com.woka.support.MainDispatcherRule
import com.woka.support.awaitTrue
import com.woka.support.awaitWriterReady
import com.woka.support.withCollector
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun onHome(block: (HomeViewModel, SettingsRepositoryImpl) -> Unit) = withTempStorageRoot {
        val repository = SettingsRepositoryImpl()
        val viewModel = HomeViewModel(repository)
        withCollector(viewModel.state) { block(viewModel, repository) }
    }

    private fun HomeViewModel.awaitFolders(expected: List<String>) =
        awaitTrue("the linked folders to become $expected") { state.value.linkedFolders == expected }

    @Test
    fun `a linked folder appears on the home screen`() {
        onHome { viewModel, repository ->
            viewModel.awaitFolders(emptyList())
            repository.addLinkedFolder("D:/Photos")
            viewModel.awaitFolders(listOf("D:/Photos"))
        }
    }

    @Test
    fun `folders are shown by name, not by the order they were linked`() {
        onHome { viewModel, repository ->
            repository.addLinkedFolder("D:/Zulu")
            repository.addLinkedFolder("D:/archive/alpha")
            repository.addLinkedFolder("D:/Media/Charlie")
            viewModel.awaitFolders(listOf("D:/archive/alpha", "D:/Media/Charlie", "D:/Zulu"))
        }
    }

    @Test
    fun `unlinking removes the folder and the session pinned to it`() {
        onHome { viewModel, repository ->
            repository.addLinkedFolder("D:/Photos")
            repository.addLinkedFolder("D:/Films")
            repository.addPinnedSession(PinnedSession("D:/Photos", 7, 30))
            repository.addPinnedSession(PinnedSession("D:/Films", 1, 0))
            viewModel.awaitFolders(listOf("D:/Films", "D:/Photos"))

            viewModel.removeLinkedFolder("D:/Photos")
            viewModel.awaitFolders(listOf("D:/Films"))
            val sessions = repository.settings.value.pinnedSessions
            assertEquals(listOf("D:/Films"), sessions.map { it.path })
        }
    }

    @Test
    fun `unlinking a folder that is no longer on disk still works`() {
        onHome { viewModel, repository ->
            val gone = Files.createTempDirectory("woka-home").toFile()
            repository.addLinkedFolder(gone.absolutePath)
            viewModel.awaitFolders(listOf(gone.absolutePath))
            assertTrue(gone.delete())
            viewModel.removeLinkedFolder(gone.absolutePath)
            viewModel.awaitFolders(emptyList())
        }
    }

    @Test
    fun `unlinking something never linked changes nothing`() {
        onHome { viewModel, repository ->
            repository.addLinkedFolder("D:/Photos")
            viewModel.awaitFolders(listOf("D:/Photos"))
            viewModel.removeLinkedFolder("D:/NeverLinked")
            viewModel.awaitFolders(listOf("D:/Photos"))
        }
    }

    @Test
    fun `the home screen has its own title setting`() {
        onHome { viewModel, repository ->
            assertTrue(viewModel.state.value.showTitles)
            repository.setShowTitles(false)
            awaitTrue("titles to follow the global setting") { !viewModel.state.value.showTitles }
            repository.setFolderShowTitles("HOME", true)
            awaitTrue("the HOME override to win") { viewModel.state.value.showTitles }
        }
    }

    @Test
    fun `pinning only accepts a real directory`() {
        onHome { viewModel, repository ->
            viewModel.pinFolder("D:/definitely/not/here")
            assertTrue(repository.settings.value.pinnedSessions.isEmpty())
            val real = Files.createTempDirectory("woka-home-pin").toFile()
            try {
                viewModel.pinFolder(real.absolutePath)
                assertEquals(listOf(real.absolutePath), repository.settings.value.pinnedSessions.map { it.path })
            } finally {
                real.deleteRecursively()
            }
        }
    }

    @Test
    fun `the linked folders survive a restart`() {
        withTempStorageRoot {
            val first = SettingsRepositoryImpl()
            first.awaitWriterReady()
            first.addLinkedFolder("D:/Photos")
            awaitTrue("the folder to reach disk") {
                WokaStorage.settingsFile.let { it.isFile && it.readText().contains("D:/Photos") }
            }
            val viewModel = HomeViewModel(SettingsRepositoryImpl())
            withCollector(viewModel.state) {
                awaitTrue("the reopened home to list the folder") {
                    viewModel.state.value.linkedFolders == listOf("D:/Photos")
                }
            }
        }
    }

    @Test
    fun `a folder is never listed twice`() {
        onHome { viewModel, repository ->
            repeat(3) { repository.addLinkedFolder("D:/Photos") }
            viewModel.awaitFolders(listOf("D:/Photos"))
            assertFalse(repository.settings.value.linkedFolders.size > 1)
        }
    }
}
