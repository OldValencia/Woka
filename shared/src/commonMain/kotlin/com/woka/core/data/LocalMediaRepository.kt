package com.woka.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class MediaItem {
    abstract val file: File
    abstract val name: String

    /**
     * Stat results captured once, on the listing thread. Sorting by date and
     * filtering hidden files used to call `lastModified()` / `isHidden` from
     * inside a comparator and a filter running on the UI thread — O(n log n)
     * syscalls per folder rebuild (§10.2). Declared per subclass, not in this
     * body: the base initialiser runs before `file` is assigned.
     */
    abstract val lastModified: Long
    abstract val isHidden: Boolean

    data class Folder(override val file: File) : MediaItem() {
        override val name: String = file.name
        override val lastModified: Long = file.lastModified()
        override val isHidden: Boolean = file.isHidden
    }
    data class Image(override val file: File) : MediaItem() {
        override val name: String = file.name
        override val lastModified: Long = file.lastModified()
        override val isHidden: Boolean = file.isHidden
    }
    data class Video(override val file: File) : MediaItem() {
        override val name: String = file.name
        override val lastModified: Long = file.lastModified()
        override val isHidden: Boolean = file.isHidden
    }
}

class LocalMediaRepository {

    suspend fun getItems(directoryPath: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles() ?: return@withContext emptyList()

        val folders = mutableListOf<MediaItem.Folder>()
        val media = mutableListOf<MediaItem>()

        for (file in files) {
            if (file.isDirectory) {
                folders.add(MediaItem.Folder(file))
            } else {
                mediaItemOrNull(file)?.let { media.add(it) }
            }
        }

        // CASE_INSENSITIVE_ORDER rather than a `lowercase()` selector: the latter
        // allocates a String per comparison (§10.8).
        folders.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        media.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        return@withContext folders + media
    }
}
