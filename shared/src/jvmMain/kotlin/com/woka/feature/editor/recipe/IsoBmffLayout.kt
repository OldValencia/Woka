package com.woka.feature.editor.recipe

import java.io.File
import java.io.RandomAccessFile

/** 32-bit size + four-letter type; a 64-bit size follows when the first is 1. */
private const val BOX_HEADER = 8

/**
 * Whether a box appended to this ISOBMFF file lands **after** everything in it (1.0.6 review).
 *
 * Its top-level boxes are walked by their sizes, which a head of a few KB cannot answer:
 * - a box of size 0 runs "to the end of the file", so an append lands *inside* it — a final
 *   `mdat` written that way absorbed the edit;
 * - a box that runs past the end is a cut-off recording or download, and the append would
 *   land inside it as well;
 * - a fragmented film ending in `mfra` is found by players from its last bytes (`mfro`), and
 *   a box after it hides the index they seek with.
 *
 * Our own box is skipped, so a file already carrying an edit answers as it did before it.
 * Asked at the write only, never per grid tile: a fragmented film has thousands of boxes.
 */
internal fun File.isoBoxesEndCleanly(): Boolean = runCatching {
    RandomAccessFile(this, "r").use { file ->
        val length = file.length()
        val header = ByteArray(BOX_HEADER + RECIPE_IDENTIFIER.size)
        var at = 0L
        var last = ""
        // A tail too short for a box, or bytes with no box type — zeros, mostly — is padding
        // a camera or a muxer left: the end, not a reason to refuse.
        while (length - at >= BOX_HEADER) {
            file.seek(at)
            file.readFully(header, 0, BOX_HEADER)
            val type = String(header, 4, 4, Charsets.US_ASCII)
            if (!type.all { it in ' '..'~' }) break
            var size = header.int(0).toLong() and 0xFFFFFFFFL
            if (size == 1L) size = file.readLong()
            if (size == 0L || at + size > length) return false
            if (size < BOX_HEADER) break
            if (!(type == "uuid" && file.isOurs(at, size))) last = type
            at += size
        }
        last != "mfra"
    }
}.getOrDefault(false)

/** A `uuid` box at [at] whose identifier is ours. */
private fun RandomAccessFile.isOurs(at: Long, size: Long): Boolean {
    if (size < BOX_HEADER + RECIPE_IDENTIFIER.size) return false
    val identifier = ByteArray(RECIPE_IDENTIFIER.size)
    seek(at + BOX_HEADER)
    readFully(identifier)
    return identifier.contentEquals(RECIPE_IDENTIFIER)
}

private fun ByteArray.int(at: Int): Int =
    ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)
