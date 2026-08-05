package com.woka.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class MediaItem {
    abstract val file: File
    abstract val name: String
    
    data class Folder(override val file: File) : MediaItem() {
        override val name: String = file.name
    }
    data class Image(override val file: File) : MediaItem() {
        override val name: String = file.name
    }
    data class Video(override val file: File) : MediaItem() {
        override val name: String = file.name
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

        folders.sortBy { it.name.lowercase() }
        media.sortBy { it.name.lowercase() }

        return@withContext folders + media
    }
}
