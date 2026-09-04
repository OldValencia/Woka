package com.woka.feature.editor.recipe

/**
 * Sixteen bytes that say the box is ours, and **they can never change**: they are the
 * identity of every recipe already written into a photograph. Readable in a hex dump
 * on purpose — someone looking at what Woka put in their file should be able to see
 * the answer without a tool.
 */
private val IDENTIFIER = "Woka-Edit-Recipe".toByteArray(Charsets.US_ASCII)

private val UUID_BOX = "uuid".toByteArray(Charsets.US_ASCII)

/** Every ISOBMFF file starts with one, which is what makes the check cheap. */
private val FTYP = "ftyp".toByteArray(Charsets.US_ASCII)

/** 32-bit size + four-letter type. */
private const val HEADER = 8

private const val OVERHEAD = HEADER + 16

/**
 * A recipe inside an ISOBMFF `uuid` box — `heic`, `heif`, `avif` (ME-108).
 *
 * **Appended after the last box, never inserted.** HEIF stores item positions in
 * `iloc` as absolute file offsets, so making room anywhere earlier silently rewrites
 * the meaning of every one of them and the picture stops decoding. Appending disturbs
 * nothing: a top-level box a reader does not recognise is skipped by its size, which
 * is exactly the extension point the specification provides.
 *
 * That is also why [window] is the tail. The rest of the app asks `carries` per
 * visible grid tile, and a box at the end of a 4 MB photograph is not in the first
 * few kilobytes.
 *
 * **The identifier does the protecting, not the box type.** A `uuid` box may hold
 * anybody's data — Canon, Adobe and GoPro all write them — so a box is ours only when
 * its UUID is [IDENTIFIER], and one that is not is left exactly where it is.
 */
internal object IsoBmffRecipe : TailRecipeCarrier {

    /** Ours, in tail coordinates — see [TailRecipeCarrier.boxIn] for why it is a range. */
    override fun boxIn(tail: ByteArray): IntRange? =
        tail.ourBox()?.let { it.at until it.end }

    override fun accepts(head: ByteArray): Boolean = head.isIsoBmff()

    /**
     * **Never truncated**, unlike the head carriers.
     *
     * Our box is the last thing in the file and [with] refuses a payload that would
     * not fit the window a reader reads, so *not finding it there* is a real answer
     * rather than a reason to read 4 MB again. The only way to defeat that is another
     * program appending 64 KB of its own after ours, which loses nothing but the edit.
     */
    override fun scan(bytes: ByteArray): RecipeScan {
        val box = bytes.ourBox() ?: return RecipeScan(null)
        val from = box.at + OVERHEAD
        return RecipeScan(String(bytes, from, box.end - from, Charsets.UTF_8))
    }

    override fun without(bytes: ByteArray): ByteArray? {
        val box = bytes.ourBox() ?: return null
        return ByteArray(bytes.size - (box.end - box.at)).also { out ->
            bytes.copyInto(out, 0, 0, box.at)
            bytes.copyInto(out, box.at, box.end, bytes.size)
        }
    }

    override fun box(payload: String): ByteArray? {
        val body = payload.toByteArray(Charsets.UTF_8)
        // Refused rather than written where a later read cannot see it — an edit that
        // is silently invisible is worse than one that says it will not fit.
        if (body.size + OVERHEAD > TAIL_WINDOW) return null

        val size = OVERHEAD + body.size
        return ByteArray(size).also { box ->
            box[0] = ((size shr 24) and 0xFF).toByte()
            box[1] = ((size shr 16) and 0xFF).toByte()
            box[2] = ((size shr 8) and 0xFF).toByte()
            box[3] = (size and 0xFF).toByte()
            UUID_BOX.copyInto(box, 4)
            IDENTIFIER.copyInto(box, HEADER)
            body.copyInto(box, OVERHEAD)
        }
    }

    /**
     * The byte path, kept for the head-carrier shape `EditRecipe` falls back to: a box
     * already carrying somebody else's bytes after it cannot be truncated away (ME-280).
     * It builds the box with [box], so the two roads cannot disagree.
     */
    override fun with(bytes: ByteArray, payload: String): ByteArray? {
        if (!accepts(bytes)) return null
        val box = box(payload) ?: return null
        return ByteArray(bytes.size + box.size).also { out ->
            bytes.copyInto(out, 0)
            box.copyInto(out, bytes.size)
        }
    }
}

private class Box(val at: Int, val end: Int)

/**
 * Ours, found by its identifier and confirmed by the header in front of it.
 *
 * Searched for rather than walked to, because a tail slice has no start to walk from:
 * the window a reader is handed begins in the middle of the picture data. The header
 * check is what keeps that honest — sixteen matching bytes inside a compressed stream
 * are astronomically unlikely, and a `uuid` box of the right size in front of them is
 * not a coincidence anything can produce by accident.
 */
private fun ByteArray.ourBox(): Box? {
    var from = 0
    while (true) {
        val found = indexOfIdentifier(from) ?: return null
        val at = found - HEADER
        if (at >= 0 && regionMatches(found - 4, UUID_BOX)) {
            val size = intAt(at)
            val end = at + size
            if (size >= OVERHEAD && end <= this.size) return Box(at, end)
        }
        from = found + 1
    }
}

private fun ByteArray.indexOfIdentifier(from: Int): Int? {
    for (at in from..size - IDENTIFIER.size) {
        if (regionMatches(at, IDENTIFIER)) return at
    }
    return null
}

private fun ByteArray.regionMatches(at: Int, what: ByteArray): Boolean {
    if (at < 0 || at + what.size > size) return false
    return what.indices.all { this[at + it] == what[it] }
}

private fun ByteArray.intAt(at: Int): Int =
    ((this[at].toInt() and 0xFF) shl 24) or
        ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or
        (this[at + 3].toInt() and 0xFF)

/** The first box is `ftyp` in every file of this family — and in nothing else. */
private fun ByteArray.isIsoBmff(): Boolean = regionMatches(4, FTYP)
