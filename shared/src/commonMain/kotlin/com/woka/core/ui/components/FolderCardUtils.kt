package com.woka.core.ui.components

import com.woka.core.data.MediaKind
import com.woka.core.data.WokaStorage
import com.woka.core.data.canDecode
import com.woka.core.data.isMedia
import com.woka.core.data.mediaKind
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
    val type = when (file.mediaKind()) {
        MediaKind.Image -> "Image"
        MediaKind.Video -> "Video"
        null -> "File"
    }
    return "$type • ${formatFileSize(file.length())}"
}

data class FolderStateData(val itemCount: Int, val previews: List<File>)

suspend fun fetchFolderStateData(file: File): FolderStateData {
    return withContext(Dispatchers.IO) {
        val allFiles = file.listFiles() ?: emptyArray()
        val count = allFiles.count { f -> f.isDirectory || f.isMedia }
        val previews = allFiles.mapNotNull { f ->
            if (!f.isFile) return@mapNotNull null
            when (f.mediaKind()) {
                // A format with no decoder would only draw an empty tile (§2).
                MediaKind.Image -> f.takeIf { it.canDecode }
                MediaKind.Video -> WokaStorage.thumbnailFile(f).takeIf { it.exists() }
                null -> null
            }
        }.take(3)
        FolderStateData(count, previews)
    }
}
