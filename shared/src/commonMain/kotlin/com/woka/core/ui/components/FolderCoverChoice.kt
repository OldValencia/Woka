package com.woka.core.ui.components

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.MediaKind
import com.woka.core.data.WokaStorage
import com.woka.core.data.mediaKind
import com.woka.core.utils.loadStillCover
import java.io.File

/**
 * How many subfolders are opened looking for a cover, at most.
 *
 * A card is a cover, not a search. A folder whose first eight subfolders are all
 * empty is one the folder glyph describes honestly, and the alternative — walking
 * five hundred subdirectories while the Home grid lays itself out — is a stutter
 * bought for a picture nobody asked for.
 */
private const val COVER_SCAN_LIMIT = 8

/**
 * How many candidates are kept before one is chosen.
 *
 * More than one because the first is not always drawable: a folder holding a single
 * fully transparent PNG drew a blank tile that read as a broken thumbnail (reported
 * 2026-08-30). Four, because each rejection costs a decode and a folder whose first
 * four pictures are all blank is a folder the glyph describes honestly.
 */
private const val COVER_CANDIDATES = 4

/** Points per axis compared before a picture is called blank — 64 reads in all. */
private const val BLANK_SAMPLES = 8

/**
 * Which picture a folder card borrows, and why it is not simply the first one.
 *
 * Split out of `FolderCardUtils` when the choice stopped being "the first file that
 * is an image" and became a small policy of its own.
 *
 * The order is: pictures lying directly inside, then pictures one level down. Within
 * each, the first candidate that is **not** a flat expanse of one colour wins.
 *
 * **The fall-through is on the chosen cover, not on the candidate list.** Written the
 * other way round first — `coverHere().ifEmpty { coverOneLevelDown() }` — it short
 * circuited on the case it was built for: the reported folder holds five subfolders
 * of photographs *and* one transparent PNG, so candidates existed, none of them was
 * drawable, and the subfolders were never opened.
 */
internal suspend fun chooseFolderCover(allFiles: Array<File>): List<File> {
    val here = allFiles.coverHere().firstDrawable()
    if (here != null) return listOf(here)
    return allFiles.coverOneLevelDown().firstDrawable()?.let { listOf(it) }.orEmpty()
}

/** The first candidate with something to look at, or null when none of them has. */
private suspend fun List<File>.firstDrawable(): File? = firstOrNull { !it.looksBlank() }

/**
 * The pictures lying directly inside, in the order the card would try them.
 *
 * **Hidden files are skipped.** A folder carrying `desktop.ini` and one photograph
 * should show the photograph; on Windows a hidden file is a normal inhabitant of a
 * user's Pictures folder and is never what the folder is *about*.
 */
private fun Array<File>.coverHere(): List<File> = asSequence()
    .filter { it.isFile && !it.isHidden }
    .mapNotNull { f ->
        when (f.mediaKind()) {
            // Every image is eligible now: the ones Coil cannot read are
            // drawn by `StillCoverImage` through Woka's own decoders.
            MediaKind.Image -> f
            MediaKind.Video -> WokaStorage.thumbnailFile(f).takeIf { it.exists() }
            null -> null
        }
    }
    .take(COVER_CANDIDATES)
    .toList()

/**
 * A folder holding **only folders** borrows the first cover it finds inside one of
 * them (2026-08-30, reported by hand: a linked folder of subfolders drew nothing).
 *
 * **One level, and no further.** A folder of folders is an ordinary shape in a
 * Linked-Folders app — a year of shoots, a season of episodes — and it deserves to
 * look like the folders inside it rather than like an empty one. Two levels is a
 * different feature: it would make an arbitrary directory tree a thing the grid
 * walks, and the depth at which a picture stops representing the folder is not a
 * number anyone can defend.
 *
 * Sorted by name, so the cover is the same on every launch. `listFiles` answers in
 * filesystem order, which on Windows is neither alphabetical nor stable.
 */
private fun Array<File>.coverOneLevelDown(): List<File> =
    filter { it.isDirectory && !it.isHidden }
        .sortedBy { it.name.lowercase() }
        .take(COVER_SCAN_LIMIT)
        .flatMap { sub -> (sub.listFiles() ?: emptyArray()).coverHere() }
        .take(COVER_CANDIDATES)

/**
 * Whether this picture has nothing to look at: every sample the same colour, alpha
 * included.
 *
 * **Why this exists.** A fully transparent PNG decodes perfectly and draws nothing,
 * so the tile showed the card's own surface and read as a broken thumbnail — with no
 * folder glyph either, because the card *had* a cover and simply could not show it
 * (reported from a screenshot, 2026-08-30). A picture of one flat colour is the same
 * problem without the transparency.
 *
 * **Sampled, not scanned.** An 8×8 grid across the preview is 64 reads against
 * 160 000 for a 400 px picture, and any photograph disagrees with itself within the
 * first few. The cost that matters is the decode, not the comparison — and it is
 * `StillPreview`'s, which writes a 400 px copy to disk and pays it once ever.
 *
 * **A decode that fails is blank too.** Nothing to draw and nothing drawable are the
 * same answer to the only question the card is asking.
 */
private suspend fun File.looksBlank(): Boolean {
    val bitmap = runCatching { loadStillCover(this) }.getOrNull() ?: return true
    if (bitmap.width <= 0 || bitmap.height <= 0) return true
    val pixels = runCatching { bitmap.toPixelMap() }.getOrNull() ?: return true

    val first = pixels[0, 0]
    for (row in 0 until BLANK_SAMPLES) {
        for (column in 0 until BLANK_SAMPLES) {
            val x = column * (bitmap.width - 1) / (BLANK_SAMPLES - 1)
            val y = row * (bitmap.height - 1) / (BLANK_SAMPLES - 1)
            if (pixels[x, y] != first) return false
        }
    }
    return true
}
