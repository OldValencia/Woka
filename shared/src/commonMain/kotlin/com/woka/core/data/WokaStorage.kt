package com.woka.core.data

import java.io.File
import java.security.MessageDigest

/**
 * Bumped whenever an existing entry stops being the right answer — because
 * [WokaStorage.thumbnailFile] names things differently, or because what goes *inside* an
 * entry changed under an unchanged name.
 *
 * v4 is the second kind (ME-177): the key is path, size and mtime, and an HDR still now
 * decodes to tone-mapped pixels without any of the three moving. Its old thumbnail would
 * otherwise outlive the change and sit dull beside a correct picture.
 */
private const val KEY_SCHEMA_MARKER = ".keys-v4"

/**
 * What an entry is written as. WebP rather than PNG since v3 — a poster is a
 * rebuildable 400 px copy, not an export, and the same picture is 32 % of the bytes.
 * See `core/ui/image/PreviewEncoding.kt` for the measurement.
 */
private const val ENTRY_SUFFIX = ".webp"

/**
 * Overrides [WokaStorage.root] when set. Exists for tests, which must never write
 * into the real `Documents/OldValencia/Woka` of whoever runs them; production
 * never sets it and takes the user-home default.
 */
const val STORAGE_ROOT_PROPERTY = "woka.storageRoot"

/**
 * Every path Woka writes to, in one place. The root used to be assembled by hand
 * in three files, and two of them derived the thumbnail-cache path independently
 * of each other.
 */
object WokaStorage {

    private val defaultRoot: File
        get() {
            val userHome = System.getProperty("user.home")
            val legacy = File(userHome, "Documents/OldValencia/Woka")
            if (legacy.exists()) return legacy
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("mac") -> File(userHome, "Library/Application Support/OldValencia/Woka")
                os.contains("linux") -> {
                    val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    if (xdg != null) File(xdg, "OldValencia/Woka")
                    else File(userHome, ".local/share/OldValencia/Woka")
                }
                else -> legacy
            }
        }

    /**
     * Read through a getter rather than captured once, so a test can point the
     * whole storage layer at a temp directory and put it back afterwards.
     */
    val root: File
        get() = System.getProperty(STORAGE_ROOT_PROPERTY)?.let { File(it) } ?: defaultRoot

    val settingsFile: File
        get() = File(root, "settings.json")

    /** Which directory [prepareThumbnailsDir] has already run for, if any. */
    private var preparedDir: File? = null

    /**
     * Poster-frame cache. The naming scheme changed with this version, so the
     * directory is dropped once on first launch rather than migrated: a thumbnail
     * is cheaper to regenerate than to move, and Woka has no users to stay
     * compatible with.
     *
     * The one-time preparation used to be a `by lazy`; it is now guarded per
     * directory instead, because [root] is no longer a constant and a `lazy` would
     * pin the cache to whichever root was seen first.
     */
    val thumbnailsDir: File
        get() {
            val dir = File(root, ".thumbnails")
            if (preparedDir != dir) prepareThumbnailsDir(dir)
            return dir
        }

    @Synchronized
    private fun prepareThumbnailsDir(dir: File) {
        if (preparedDir == dir) return
        if (!File(dir, KEY_SCHEMA_MARKER).exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
            if (dir.isDirectory) File(dir, KEY_SCHEMA_MARKER).createNewFile()
        }
        preparedDir = dir
    }

    /**
     * Where [source]'s poster frame lives.
     *
     * Keyed on the absolute path plus size and mtime. The previous key was
     * `name.hashCode()`, which gave two `clip.mp4` files in different folders one
     * shared thumbnail — the second folder showed a frame from the first — could
     * produce negative names, and never noticed a file replaced in place.
     *
     * [frameIndex] separates the frames of a frame set, which are different
     * pictures inside one file. **Zero hashes exactly as it always did**, so
     * every poster already on disk stays valid and no schema bump is needed.
     */
    fun thumbnailFile(source: File, frameIndex: Int = 0): File =
        File(thumbnailsDir, "${cacheKey(source, frameIndex)}$ENTRY_SUFFIX")

    /**
     * Every poster currently on disk, and nothing else that lives beside them —
     * [KEY_SCHEMA_MARKER] is not an entry, and a sweep that deleted it would wipe the
     * whole directory on the next launch.
     */
    fun thumbnailEntries(): List<File> =
        thumbnailsDir
            .listFiles { file: File -> file.isFile && file.name.endsWith(ENTRY_SUFFIX) }
            .orEmpty()
            .toList()

    /** SHA-256 truncated to 64 bits: deterministic, positive, collision-free in practice. */
    private fun cacheKey(source: File, frameIndex: Int): String {
        val frame = if (frameIndex == 0) "" else "|frame$frameIndex"
        // The view mode joins path, size and mtime for the reason ME-119 gives: a disk
        // entry written under one mode is not the answer under another, and none of the
        // other three moves when the mode does.
        val input =
            "${source.absolutePath}|${source.length()}|${source.lastModified()}$frame" +
                HdrView.cacheSuffix
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
