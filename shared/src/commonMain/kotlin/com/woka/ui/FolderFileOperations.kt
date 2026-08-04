package com.woka.ui

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
            settingsRepository.clearPlaybackPosition(it)
        }
        refresh()
        clearSelection()
    }
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
