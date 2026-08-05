package com.woka.core.ui.components

import com.woka.core.data.MediaKind
import com.woka.core.data.WokaStorage
import com.woka.core.data.canDecode
import com.woka.core.data.isMedia
import com.woka.core.data.mediaKind
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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

private data class FolderStateStamp(val directory: Long, val thumbnails: Long)

private val folderStateCache = ConcurrentHashMap<String, Pair<FolderStateStamp, FolderStateData>>()

/**
 * The directory's own mtime is not enough on its own: a video preview appears only
 * once its poster frame has been written, which does not touch the folder. So the
 * thumbnail directory's mtime is part of the key — otherwise a card would keep its
 * pre-thumbnail cover until the folder's contents changed (§10.3).
 */
private fun stampOf(file: File) = FolderStateStamp(file.lastModified(), WokaStorage.thumbnailsDir.lastModified())

suspend fun fetchFolderStateData(file: File): FolderStateData {
    return withContext(Dispatchers.IO) {
        val key = file.absolutePath
        val stamp = stampOf(file)
        folderStateCache[key]?.let { (cached, data) ->
            if (cached == stamp) return@withContext data
        }
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
        FolderStateData(count, previews).also { folderStateCache[key] = stamp to it }
    }
}
