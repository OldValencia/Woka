package com.woka.support

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.woka.core.data.LocalMediaRepository
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.folder.FolderViewModel
import java.io.File
import java.nio.file.Files

/**
 * The real ViewModel over a real directory, on a throwaway settings root.
 *
 * `Dispatchers.Main` has to be replaced for the scope to exist at all (see
 * [MainDispatcherRule]), but the work underneath — the listing on
 * `Dispatchers.IO`, the sort on `Dispatchers.Default` — stays real, which is why
 * the assertions poll. [withCollector] keeps the `WhileSubscribed` state hot.
 */
fun onFolder(block: (FolderViewModel, File) -> Unit) = withTempStorageRoot {
    val dir = Files.createTempDirectory("woka-folder").toFile()
    try {
        val viewModel = FolderViewModel(dir.absolutePath, LocalMediaRepository(), SettingsRepositoryImpl())
        withCollector(viewModel.state) { block(viewModel, dir) }
    } finally {
        dir.deleteRecursively()
    }
}

fun FolderViewModel.awaitNames(expected: List<String>) =
    awaitTrue("the listing to become $expected") { state.value.items.map { it.name } == expected }

/**
 * The same ViewModel, with its listing already on screen, under a Compose test.
 *
 * The listing is awaited **outside** `runComposeUiTest`: it lands on real threads
 * (`getItems` on IO, the sort on Default) that the Compose clock knows nothing
 * about, so waiting for it inside would mean polling from the very thread the
 * runner drives. `WhileSubscribed(5000)` carries the value across the handover,
 * and the composable becomes the next collector.
 */
@OptIn(ExperimentalTestApi::class)
fun onUiWithFolder(files: List<String>, block: ComposeUiTest.(FolderViewModel, File) -> Unit) =
    withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-folder-ui").toFile()
        try {
            val viewModel =
                FolderViewModel(dir.absolutePath, LocalMediaRepository(), SettingsRepositoryImpl())
            files.forEach { dir.media(it) }
            viewModel.refresh()
            withCollector(viewModel.state) {
                viewModel.awaitNames(files.sorted())
                runComposeUiTest { block(viewModel, dir) }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

/** A file carrying a chosen timestamp — date sorting needs distinct real mtimes. */
fun File.media(name: String, modifiedAt: Long = 1_000_000_000_000L): File =
    File(this, name).also {
        it.writeText("x")
        it.setLastModified(modifiedAt)
    }
