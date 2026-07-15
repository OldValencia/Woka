package io.iris.ui

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
                val nameWithoutExt = file.nameWithoutExtension
                val ext = file.extension
                val parent = file.parentFile
                var newName = if (ext.isNotEmpty()) "$nameWithoutExt copy.$ext" else "$nameWithoutExt copy"
                var newFile = File(parent, newName)
                var counter = 1
                while (newFile.exists()) {
                    newName = if (ext.isNotEmpty()) "$nameWithoutExt copy $counter.$ext" else "$nameWithoutExt copy $counter"
                    newFile = File(parent, newName)
                    counter++
                }
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
                if (file.exists()) {
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
                    val newFile = File(destDir, file.name)
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
