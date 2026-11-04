package com.woka.feature.editor.recipe

/**
 * The byte helpers the Matroska and RIFF tail carriers share (ME-282, ME-283).
 *
 * [RECIPE_IDENTIFIER] is `IsoBmffRecipe`'s sixteen bytes spelled a second time rather than
 * shared, because ME-282 leaves that carrier byte-identical. It is the **same** value and
 * must stay so: it is what "ours" means in every container, and a box we assigned an id to
 * ourselves is only as trustworthy as the identifier inside it.
 */
internal val RECIPE_IDENTIFIER = "Woka-Edit-Recipe".toByteArray(Charsets.US_ASCII)

/** The first [RECIPE_IDENTIFIER] at or after [from], or null. */
internal fun ByteArray.identifierAt(from: Int): Int? {
    for (at in from..size - RECIPE_IDENTIFIER.size) {
        if (matchesAt(at, RECIPE_IDENTIFIER)) return at
    }
    return null
}

internal fun ByteArray.matchesAt(at: Int, what: ByteArray): Boolean {
    if (at < 0 || at + what.size > size) return false
    return what.indices.all { this[at + it] == what[it] }
}

/** This array without [range] — the byte path's clear. */
internal fun ByteArray.removing(range: IntRange): ByteArray =
    ByteArray(size - (range.last + 1 - range.first)).also { out ->
        copyInto(out, 0, 0, range.first)
        copyInto(out, range.first, range.last + 1, size)
    }

/** This array with [tail] after it — the byte path's write. */
internal fun ByteArray.appending(tail: ByteArray): ByteArray =
    ByteArray(size + tail.size).also { out ->
        copyInto(out, 0)
        tail.copyInto(out, size)
    }

/** Unsigned little-endian 32 bits, as RIFF counts. */
internal fun ByteArray.le32(at: Int): Long =
    (this[at].toLong() and 0xFF) or
        ((this[at + 1].toLong() and 0xFF) shl 8) or
        ((this[at + 2].toLong() and 0xFF) shl 16) or
        ((this[at + 3].toLong() and 0xFF) shl 24)

internal fun le32Bytes(value: Long): ByteArray =
    ByteArray(4) { byte -> ((value shr (8 * byte)) and 0xFF).toByte() }
