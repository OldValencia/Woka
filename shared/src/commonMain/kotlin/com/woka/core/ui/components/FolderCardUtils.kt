package com.woka.core.ui.components

import java.io.File
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    val value = size / 1024.0.pow(digitGroups.toDouble())
    return "${(value * 10).toInt() / 10.0} ${units[digitGroups]}".replace(".0 ", " ")
}

fun getFolderCardSubtitle(isDir: Boolean, file: File): String {
    if (isDir) return "Folder"
    val ext = file.extension.lowercase()
    val type = when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> "Image"
        "mp4", "mkv", "avi", "mov", "webm" -> "Video"
        else -> "File"
    }
    return "$type • ${formatFileSize(file.length())}"
}

data class FolderStateData(val itemCount: Int, val previews: List<File>)

suspend fun fetchFolderStateData(file: File): FolderStateData {
    return withContext(Dispatchers.IO) {
        val allFiles = file.listFiles() ?: emptyArray()
        val count = allFiles.count { f ->
            val ext = f.extension.lowercase()
            f.isDirectory || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "mp4", "mkv", "avi", "mov", "webm")
        }
        val previews = allFiles.mapNotNull { f ->
            if (!f.isFile) return@mapNotNull null
            val ext = f.extension.lowercase()
            if (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic")) {
                f
            } else if (ext in listOf("mp4", "mkv", "avi", "mov", "webm")) {
                val previewFile = File(System.getProperty("user.home"), "Documents/OldValencia/Woka/.thumbnails/${f.name.hashCode()}.png")
                if (previewFile.exists()) previewFile else null
            } else {
                null
            }
        }.take(3)
        FolderStateData(count, previews)
    }
}
