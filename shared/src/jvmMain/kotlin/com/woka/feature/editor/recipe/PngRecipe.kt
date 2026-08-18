package com.woka.feature.editor.recipe

import java.util.zip.CRC32

private val SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
)

/**
 * Four letters, none of them decoration. Lower-case first: **ancillary**, a decoder
 * may ignore it. Lower-case second: **private**, not a registered type. Upper-case
 * third: required by the spec, reserved. Lower-case fourth: **safe to copy**, so an
 * editor that does not understand the chunk may still carry it across.
 */
private val TYPE = "wkEd".toByteArray(Charsets.US_ASCII)

/** Where the picture starts, and therefore where the walk can stop looking. */
private val IDAT = "IDAT".toByteArray(Charsets.US_ASCII)

/** Signature(8) + IHDR's own length(4) + type(4) + data(13) + CRC(4). */
private const val AFTER_HEADER = 33

private const val FRAME = 12

/**
 * A recipe inside a PNG's own ancillary private chunk.
 *
 * Placed straight after `IHDR`, which is always the first chunk. Nothing constrains
 * where an ancillary chunk of this kind sits, and putting it first means a reader
 * working from the head of a large file finds it in the first few dozen bytes.
 */
internal object PngRecipe : RecipeCarrier {

    override fun scan(bytes: ByteArray): RecipeScan {
        val walk = bytes.pngWalk()
        val ours = walk.chunks.firstOrNull { it.isOurs(bytes) }
            ?: return RecipeScan(null, walk.truncated)
        return RecipeScan(String(bytes, ours.at + 8, ours.end - FRAME - ours.at, Charsets.UTF_8))
    }

    override fun without(bytes: ByteArray): ByteArray? {
        val ours = bytes.pngWalk().chunks.filter { it.isOurs(bytes) }
        if (ours.isEmpty()) return null
        val kept = ByteArray(bytes.size - ours.sumOf { it.end - it.at })
        var written = 0
        var from = 0
        ours.forEach { chunk ->
            bytes.copyInto(kept, written, from, chunk.at)
            written += chunk.at - from
            from = chunk.end
        }
        bytes.copyInto(kept, written, from, bytes.size)
        return kept
    }

    override fun with(bytes: ByteArray, payload: String): ByteArray? {
        if (!bytes.pngWalk().valid || bytes.size < AFTER_HEADER) return null
        val body = payload.toByteArray(Charsets.UTF_8)
        val chunk = chunkOf(body)
        return ByteArray(bytes.size + chunk.size).also { out ->
            bytes.copyInto(out, 0, 0, AFTER_HEADER)
            chunk.copyInto(out, AFTER_HEADER)
            bytes.copyInto(out, AFTER_HEADER + chunk.size, AFTER_HEADER)
        }
    }
}

/** Length, type, data, and the CRC over type and data — never over the length. */
private fun chunkOf(body: ByteArray): ByteArray {
    val chunk = ByteArray(FRAME + body.size)
    chunk.putInt(0, body.size)
    TYPE.copyInto(chunk, 4)
    body.copyInto(chunk, 8)
    val crc = CRC32()
    crc.update(chunk, 4, 4 + body.size)
    chunk.putInt(8 + body.size, crc.value.toInt())
    return chunk
}

private class Chunk(val at: Int, val end: Int) {

    fun isOurs(bytes: ByteArray): Boolean =
        TYPE.indices.all { bytes[at + 4 + it] == TYPE[it] }
}

private class PngWalk(val chunks: List<Chunk>, val truncated: Boolean, val valid: Boolean)

/**
 * Every chunk in the file, in order.
 *
 * **Stops at `IDAT`**, which is what makes reading the head of a file enough: our
 * chunk is always before the picture data, so reaching the picture data is a
 * definite "there is no recipe here" rather than a walk that ran out. Without this
 * every unedited PNG would report truncated — `IDAT` is larger than any sane head
 * window — and be read a second time in full.
 *
 * A negative declared length is a corrupt file rather than a huge one — the spec
 * caps a chunk at 2³¹−1 — and both it and a length running past the end stop the
 * walk instead of throwing.
 */
private fun ByteArray.pngWalk(): PngWalk {
    if (size < AFTER_HEADER || SIGNATURE.indices.any { this[it] != SIGNATURE[it] }) {
        return PngWalk(emptyList(), truncated = false, valid = false)
    }
    val chunks = mutableListOf<Chunk>()
    var at = SIGNATURE.size

    while (true) {
        if (at + FRAME > size) return PngWalk(chunks, truncated = at != size, valid = true)
        if (IDAT.indices.all { this[at + 4 + it] == IDAT[it] }) {
            return PngWalk(chunks, truncated = false, valid = true)
        }
        val length = getInt(at)
        if (length < 0) return PngWalk(chunks, truncated = false, valid = true)
        val end = at + FRAME + length
        if (end > size) return PngWalk(chunks, truncated = true, valid = true)
        chunks += Chunk(at, end)
        at = end
    }
}

private fun ByteArray.getInt(at: Int): Int =
    ((this[at].toInt() and 0xFF) shl 24) or
        ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or
        (this[at + 3].toInt() and 0xFF)

private fun ByteArray.putInt(at: Int, value: Int) {
    this[at] = ((value shr 24) and 0xFF).toByte()
    this[at + 1] = ((value shr 16) and 0xFF).toByte()
    this[at + 2] = ((value shr 8) and 0xFF).toByte()
    this[at + 3] = (value and 0xFF).toByte()
}
