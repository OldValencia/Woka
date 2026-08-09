package com.woka.feature.folder

import com.woka.core.utils.FrameExportStatus
import com.woka.core.utils.exportFramesToPng
import java.io.File
import kotlinx.coroutines.launch

/**
 * Filesystem operations on the folder, split out of [FolderViewModel] per the §2
 * file-size mandate — the same extension-function pattern the player package uses
 * for `VideoPlayerControls`. Each op runs [FolderActions], then refreshes the
 * listing so the grid reflects the change.
 */

fun FolderViewModel.deleteFiles(paths: List<String>) {
    scope.launch {
        FolderActions.deleteFiles(paths)
        // A deleted video must not leave a stale record behind — neither its tracks
        // (§2.6) nor its resume position (§4).
        paths.forEach {
            settingsRepository.clearTrackPrefsForFile(it)
            settingsRepository.clearWatchRecord(it)
        }
        refresh()
        clearSelection()
    }
}

/**
 * Marks a video watched without playing it — the same record a full playthrough
 * leaves, so the grid draws it identically. No duration is known here and none is
 * needed: finished fills the bar whatever the length.
 */
fun FolderViewModel.markWatched(path: String) {
    settingsRepository.setWatchFinished(path, durationMs = 0L)
}

/** Withdraws the mark, and any resume point with it. */
fun FolderViewModel.markUnwatched(path: String) {
    settingsRepository.clearWatchRecord(path)
}

fun FolderViewModel.duplicateFiles(paths: List<String>) {
    scope.launch {
        FolderActions.duplicateFiles(paths)
        refresh()
    }
}

fun FolderViewModel.createFolder(name: String) {
    scope.launch {
        FolderActions.createFolder(path, name)
        refresh()
    }
}

fun FolderViewModel.moveFiles(paths: List<String>, destinationPath: String) {
    scope.launch {
        FolderActions.moveFiles(paths, destinationPath)
        refresh()
        clearSelection()
    }
}

fun FolderViewModel.renameFile(path: String, newName: String) {
    scope.launch {
        FolderActions.renameFile(path, newName)
        refresh()
        clearSelection()
    }
}

fun FolderViewModel.copyFiles(paths: List<String>, destinationPath: String) {
    scope.launch {
        FolderActions.copyFiles(paths, destinationPath)
        refresh()
        clearSelection()
    }
}

/**
 * Writes every picture inside one file out as PNG. No [refresh] afterwards: the
 * export lands in a folder the user chose, which is usually not this one.
 */
fun FolderViewModel.exportFrames(path: String, destinationPath: String) {
    scope.launch {
        val source = File(path)
        FrameExportStatus.progress(source.name, 0, 1)
        val result = runCatching {
            exportFramesToPng(source, File(destinationPath)) { done, total ->
                FrameExportStatus.progress(source.name, done, total)
            }
        }
        result.fold(
            onSuccess = { FrameExportStatus.finished(it) },
            onFailure = { FrameExportStatus.failed(it.message ?: "unknown error") }
        )
    }
}
