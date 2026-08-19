package com.woka.feature.editor.recipe

/** The canvas a simple WebP declares, which is what a `VP8X` has to repeat. */
internal class WebpCanvas(val width: Int, val height: Int, val hasAlpha: Boolean)

/**
 * Where the size of a simple WebP is actually written (ME-137).
 *
 * A simple file has no canvas of its own — the bitstream *is* the canvas — so promoting
 * one to the extended container means copying its dimensions into a `VP8X`. Get them
 * wrong and every reader crops or stretches the picture to whatever was written, which
 * is why this is a file of its own with its own tests rather than four lines inside the
 * carrier.
 *
 * Read straight out of the two bitstream headers. Neither needs a decoder: both put the
 * size in the first handful of bytes, in shapes fixed by their specifications.
 */
internal fun webpCanvasOf(bytes: ByteArray, type: String, at: Int, size: Int): WebpCanvas? =
    when (type) {
        "VP8 " -> lossyCanvas(bytes, at, size)
        "VP8L" -> losslessCanvas(bytes, at, size)
        else -> null
    }

/**
 * VP8 key frame: a 3-byte frame tag, the start code `9D 01 2A`, then two 16-bit
 * little-endian fields whose low 14 bits are the dimensions — the top two are a scale
 * hint no encoder Woka meets uses.
 *
 * A non-key first frame is not a still picture, and answering null leaves the file in
 * the bucket it is in rather than writing a canvas guessed from nothing.
 */
private fun lossyCanvas(bytes: ByteArray, at: Int, size: Int): WebpCanvas? {
    if (size < 10 || at + 10 > bytes.size) return null
    if (bytes[at].toInt() and 1 != 0) return null
    if (bytes[at + 3] != 0x9D.toByte() ||
        bytes[at + 4] != 0x01.toByte() ||
        bytes[at + 5] != 0x2A.toByte()
    ) {
        return null
    }
    return WebpCanvas(
        width = bytes.leShort(at + 6) and 0x3FFF,
        height = bytes.leShort(at + 8) and 0x3FFF,
        // Lossy alpha travels in its own `ALPH` chunk, which already makes the file
        // extended — so a *simple* lossy file never has any.
        hasAlpha = false
    )
}

/**
 * VP8L: the `0x2F` signature, then 32 little-endian bits — width−1 in the low 14,
 * height−1 in the next 14, and the alpha hint in bit 28.
 */
private fun losslessCanvas(bytes: ByteArray, at: Int, size: Int): WebpCanvas? {
    if (size < 5 || at + 5 > bytes.size) return null
    if (bytes[at] != 0x2F.toByte()) return null
    val bits = bytes.leInt(at + 1)
    return WebpCanvas(
        width = (bits and 0x3FFF) + 1,
        height = ((bits ushr 14) and 0x3FFF) + 1,
        hasAlpha = (bits ushr 28) and 1 == 1
    )
}

private fun ByteArray.leShort(at: Int): Int =
    (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.leInt(at: Int): Int =
    (this[at].toInt() and 0xFF) or
        ((this[at + 1].toInt() and 0xFF) shl 8) or
        ((this[at + 2].toInt() and 0xFF) shl 16) or
        ((this[at + 3].toInt() and 0xFF) shl 24)
