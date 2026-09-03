package com.woka.feature.folder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

object FolderActions {
    suspend fun deleteFiles(paths: List<String>) = withContext(Dispatchers.IO) {
        val desktop = Desktop.getDesktop()
        paths.forEach { path ->
            val file = File(path)
            if (file.exists()) {
                if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                    desktop.moveToTrash(file)
                } else {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            }
        }
    }

    suspend fun duplicateFiles(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { path ->
            val file = File(path)
            if (file.exists()) {
                val newFile = copyNameBeside(file.parentFile, file)
                if (file.isDirectory) {
                    file.copyRecursively(newFile)
                } else {
                    file.copyTo(newFile)
                }
            }
        }
    }

    suspend fun createFolder(parentPath: String, name: String) = withContext(Dispatchers.IO) {
        var folderName = name
        var newFile = File(parentPath, folderName)
        var counter = 1
        while (newFile.exists()) {
            folderName = "$name $counter"
            newFile = File(parentPath, folderName)
            counter++
        }
        newFile.mkdirs()
    }

    suspend fun moveFiles(paths: List<String>, destinationPath: String) = withContext(Dispatchers.IO) {
        val destDir = File(destinationPath)
        if (destDir.exists() && destDir.isDirectory) {
            paths.forEach { path ->
                val file = File(path)
                // Moving a file into the folder it is already in is nothing to do, and
                // saying so beats relying on `renameTo` to no-op: the fallback below
                // copies and then **deletes the source**, which for a self-move is the
                // same catastrophe `copyFiles` had.
                if (file.exists() && !isSamePlace(destDir, file)) {
                    val newFile = File(destDir, file.name)
                    if (!file.renameTo(newFile)) {
                        if (file.isDirectory) {
                            file.copyRecursively(newFile)
                            file.deleteRecursively()
                        } else {
                            file.copyTo(newFile)
                            file.delete()
                        }
                    }
                }
            }
        }
    }

    suspend fun renameFile(path: String, newName: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) {
            val newFile = File(file.parentFile, newName)
            if (!newFile.exists()) {
                file.renameTo(newFile)
            }
        }
    }

    suspend fun copyFiles(paths: List<String>, destinationPath: String) = withContext(Dispatchers.IO) {
        val destDir = File(destinationPath)
        if (destDir.exists() && destDir.isDirectory) {
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val newFile = targetFor(destDir, file)
                    if (file.isDirectory) {
                        file.copyRecursively(newFile, overwrite = true)
                    } else {
                        file.copyTo(newFile, overwrite = true)
                    }
                }
            }
        }
    }
}

/** Whether [dir] is the folder [file] already sits in, compared canonically. */
private fun isSamePlace(dir: File, file: File): Boolean =
    runCatching { dir.canonicalFile == file.parentFile?.canonicalFile }.getOrDefault(false)

/**
 * Where a copy of [file] should land in [dir] — **its own name, unless that would be
 * the file itself**.
 *
 * This is a data-loss guard, not a naming convenience. `File.copyTo(overwrite = true)`
 * deletes the target *before* it opens the source, so a copy onto itself removes the
 * photograph and then fails reading it — nothing is written and the original is gone.
 * Reported 2026-09-03: "Duplicate to…", the same folder chosen, and the file vanished
 * with a `FileNotFoundException` from `FileInputStream.open`.
 *
 * The guard lives here rather than in the context menu because the menu is not the only
 * caller and the next one will not remember.
 */
private fun targetFor(dir: File, file: File): File =
    if (isSamePlace(dir, file)) copyNameBeside(dir, file) else File(dir, file.name)

/**
 * `photo copy.jpg`, then `photo copy 2.jpg` — the name `duplicateFiles` has always
 * given, now written once so the two cannot drift.
 */
private fun copyNameBeside(dir: File, file: File): File {
    val stem = file.nameWithoutExtension
    val ext = file.extension
    fun named(suffix: String) =
        File(dir, if (ext.isNotEmpty()) "$stem copy$suffix.$ext" else "$stem copy$suffix")

    var candidate = named("")
    var counter = 1
    while (candidate.exists()) {
        candidate = named(" $counter")
        counter++
    }
    return candidate
}
