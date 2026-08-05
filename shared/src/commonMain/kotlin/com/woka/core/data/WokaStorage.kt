package com.woka.core.data

import java.io.File
import java.security.MessageDigest

/** Bumped whenever thumbnailFile changes how it names things. */
private const val KEY_SCHEMA_MARKER = ".keys-v2"

/**
 * Every path Woka writes to, in one place. The root used to be assembled by hand
 * in three files, and two of them derived the thumbnail-cache path independently
 * of each other.
 */
object WokaStorage {

    val root: File = File(System.getProperty("user.home"), "Documents/OldValencia/Woka")

    val settingsFile: File
        get() = File(root, "settings.json")

    /**
     * Poster-frame cache. The naming scheme changed with this version, so the
     * directory is dropped once on first launch rather than migrated: a thumbnail
     * is cheaper to regenerate than to move, and Woka has no users to stay
     * compatible with.
     */
    val thumbnailsDir: File by lazy {
        val dir = File(root, ".thumbnails")
        if (!File(dir, KEY_SCHEMA_MARKER).exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
            if (dir.isDirectory) File(dir, KEY_SCHEMA_MARKER).createNewFile()
        }
        dir
    }

    /**
     * Where [source]'s poster frame lives.
     *
     * Keyed on the absolute path plus size and mtime. The previous key was
     * `name.hashCode()`, which gave two `clip.mp4` files in different folders one
     * shared thumbnail — the second folder showed a frame from the first — could
     * produce negative names, and never noticed a file replaced in place.
     */
    fun thumbnailFile(source: File): File = File(thumbnailsDir, "${cacheKey(source)}.png")

    /** SHA-256 truncated to 64 bits: deterministic, positive, collision-free in practice. */
    private fun cacheKey(source: File): String {
        val input = "${source.absolutePath}|${source.length()}|${source.lastModified()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
