package com.woka.feature.editor.recipe

import java.io.ByteArrayOutputStream

/** Extension Introducer, and the label that makes it an Application Extension. */
private const val EXTENSION = 0x21
private const val APPLICATION = 0xFF

/** Identifier (8) + authentication code (3), fixed forever — ME-108's argument. */
internal const val GIF_OURS = "WokaEditRec"

/** The one sub-block length the format fixes: [GIF_OURS] is announced as eleven bytes. */
internal const val GIF_IDENTIFIER_SIZE = 0x0B

/** A sub-block's length is one byte, so a payload is written in runs of this. */
private const val MAX_SUB_BLOCK = 255

/** Signature (6) + logical screen descriptor (7), after which the tables begin. */
private const val HEADER = 13

/** Offset of the packed field: bit 7 is "a Global Color Table follows". */
private const val PACKED = 10

private const val GCT_FLAG = 0x80
private const val GCT_SIZE_MASK = 0x07

/**
 * The GIF block walk and the sub-block framing, split out of [GifRecipe] under the size
 * mandate — the same cut `TiffIfd` made beside `TiffRecipe`. The carrier decides what
 * goes in the file; this knows how the file is laid out.
 */

/** Where ours is, if it is there, and where one would go. */
internal class GifWalk(val ours: GifBlock?, val insertAt: Int, val truncated: Boolean)

/** Half-open: [at] is the introducer, [end] is one past the block terminator. */
internal class GifBlock(val at: Int, val end: Int)

/**
 * The leading run of application extensions, and nothing beyond it.
 *
 * The walk stops at the first non-application block, and that **is** the answer: Woka
 * never writes past that point, so reaching the picture data decides "no recipe"
 * rather than reporting a window that ran out. That is what keeps the question cheap
 * enough for the grid to ask it per tile (`EditRecipe`).
 *
 * Null is "not a GIF this can read", which refuses every operation rather than guessing
 * at a byte stream whose shape was not understood.
 */
internal fun ByteArray.gifWalk(): GifWalk? {
    if (size < HEADER) return null
    if (!gifMatches(0, "GIF89a") && !gifMatches(0, "GIF87a")) return null

    val packed = this[PACKED].toInt() and 0xFF
    // A Global Color Table is 768 bytes at its largest, so one that runs past the end
    // is a broken file rather than a window that was too small.
    var at = HEADER
    if (packed and GCT_FLAG != 0) at += 3 shl ((packed and GCT_SIZE_MASK) + 1)
    if (at > size) return null

    var insertAt = at
    var ours: GifBlock? = null
    while (true) {
        if (at + 2 > size) return GifWalk(ours, insertAt, truncated = true)
        val isApplication = (this[at].toInt() and 0xFF) == EXTENSION &&
            (this[at + 1].toInt() and 0xFF) == APPLICATION
        // A graphic control block, an image descriptor or the trailer: the picture
        // starts here and nothing of ours is ever written past it.
        if (!isApplication) return GifWalk(ours, insertAt, truncated = false)

        // From the first sub-block, identifier included: the format fixes that one at
        // eleven bytes, but a chain walked from the front does not have to trust it.
        val end = subBlocksEnd(at + 2) ?: return GifWalk(ours, insertAt, truncated = true)
        if (isOurs(at)) ours = GifBlock(at, end) else insertAt = end
        at = end
    }
}

/** Whether the block at [at] announces eleven bytes and they spell [GIF_OURS]. */
private fun ByteArray.isOurs(at: Int): Boolean {
    if (at + 3 + GIF_IDENTIFIER_SIZE > size) return false
    if ((this[at + 2].toInt() and 0xFF) != GIF_IDENTIFIER_SIZE) return false
    return gifMatches(at + 3, GIF_OURS)
}

/** One past the `0x00` that ends the chain, or null when the bytes ran out first. */
private fun ByteArray.subBlocksEnd(from: Int): Int? {
    var at = from
    while (at < size) {
        val length = this[at].toInt() and 0xFF
        if (length == 0) return at + 1
        at += 1 + length
    }
    return null
}

/** The chain's payload, concatenated — the framing is the whole of the fiddliness. */
internal fun ByteArray.gifSubBlockData(from: Int): ByteArray {
    val out = ByteArrayOutputStream()
    var at = from
    while (at < size) {
        val length = this[at].toInt() and 0xFF
        if (length == 0 || at + 1 + length > size) break
        out.write(this, at + 1, length)
        at += 1 + length
    }
    return out.toByteArray()
}

/** Our whole block: introducer, label, identity, the payload in runs, terminator. */
internal fun gifBlockOf(payload: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(EXTENSION)
    out.write(APPLICATION)
    out.write(GIF_IDENTIFIER_SIZE)
    out.write(GIF_OURS.toByteArray(Charsets.US_ASCII))
    var at = 0
    while (at < payload.size) {
        val run = minOf(MAX_SUB_BLOCK, payload.size - at)
        out.write(run)
        out.write(payload, at, run)
        at += run
    }
    out.write(0)
    return out.toByteArray()
}

internal fun ByteArray.gifMatches(at: Int, text: String): Boolean =
    at + text.length <= size && text.indices.all { this[at + it] == text[it].code.toByte() }
