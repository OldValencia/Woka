package com.woka.core.ui.components

import com.woka.core.data.MediaKind
import com.woka.core.data.WokaStorage
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
    return "$type · ${formatFileSize(file.length())}"
}

/**
 * Shortens a name from the middle rather than the end. What tells two long names
 * apart is usually in the tail — `f0fb96fb…19e18f` is identifiable where
 * `f0fb96fb-49f7-…` is not, and a linked folder really can be named after a UUID.
 *
 * Deliberately generous: this is the guard against a pathological name, not the
 * fit-to-width rule. Width is still handled by `TextOverflow.Ellipsis` on the label,
 * which is the only thing that knows how wide the card was actually drawn.
 */
fun middleEllipsis(name: String, max: Int = 26): String {
    if (name.length <= max) return name
    val head = (max - 1) / 2
    return name.take(head) + "…" + name.takeLast(max - 1 - head)
}

/**
 * The second line of a folder card's caption. One line replaced two: the word
 * "Folder" under the cover and the "51 ITEMS" badge on it said the same thing from
 * two places, both fighting the picture.
 */
fun folderCardMeta(itemCount: Int): String = when (itemCount) {
    0 -> "Folder · empty"
    1 -> "Folder · 1 item"
    else -> "Folder · $itemCount items"
}

/** Splices a multi-frame still's frame count into its "Image · 2.1 MB" line. */
fun withFrameCount(subtitle: String, frameCount: Int): String =
    subtitle.replaceFirst(" · ", " · $frameCount frames · ")

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
                // Every image is eligible now: the ones Coil cannot read are
                // drawn by `StillCoverImage` through Woka's own decoders.
                MediaKind.Image -> f
                MediaKind.Video -> WokaStorage.thumbnailFile(f).takeIf { it.exists() }
                null -> null
            }
        }.take(1)
        FolderStateData(count, previews).also { folderStateCache[key] = stamp to it }
    }
}
