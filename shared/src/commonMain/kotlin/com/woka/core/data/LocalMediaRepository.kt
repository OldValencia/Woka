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
    private val supportedImageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp")
    private val supportedVideoExtensions = setOf("mp4", "mkv", "avi", "mov", "webm")

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
                val ext = file.extension.lowercase()
                if (supportedImageExtensions.contains(ext)) {
                    media.add(MediaItem.Image(file))
                } else if (supportedVideoExtensions.contains(ext)) {
                    media.add(MediaItem.Video(file))
                }
            }
        }

        folders.sortBy { it.name.lowercase() }
        media.sortBy { it.name.lowercase() }

        return@withContext folders + media
    }
}
