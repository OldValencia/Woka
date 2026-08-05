package com.woka.support

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

/** A file carrying a chosen timestamp — date sorting needs distinct real mtimes. */
fun File.media(name: String, modifiedAt: Long = 1_000_000_000_000L): File =
    File(this, name).also {
        it.writeText("x")
        it.setLastModified(modifiedAt)
    }
