package com.woka.feature.editor.recipe

private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)

private val AVI = "AVI ".toByteArray(Charsets.US_ASCII)

/** Private, and nothing else writes it — `WebpRecipe`'s chunk name, in the other RIFF. */
private val OURS = "WKED".toByteArray(Charsets.US_ASCII)

/** Four-letter id + little-endian size. */
private const val HEADER = 8

private const val OVERHEAD = HEADER + 16

/** `RIFF` plus the size field itself, which the size does not count. */
private const val RIFF_PREFIX = 8L

/** The `RIFF` size sits here, little-endian, in every RIFF file. */
private const val SIZE_AT = 4L

/**
 * A recipe in a chunk at the end of an `avi`'s `RIFF` (ME-283).
 *
 * **A tail carrier with one header field.** The chunk goes last *inside* the `RIFF`, after
 * `idx1`, where an unknown chunk is skipped by its size — the extension point RIFF has.
 * Unlike ISOBMFF and Matroska the `RIFF` counts its own length at offset 4, so a write
 * also rewrites those four bytes ([lengthPatch]) — after the append, so a write that dies
 * between leaves a size that is merely short, which readers tolerate.
 *
 * **Refused unless the `RIFF` ends where the file does** ([fits]). An OpenDML file over
 * 1 GB is a chain of `RIFF AVIX` after the first, and a chunk appended to the last one
 * would need that one's size found and rewritten — a different and unmeasured claim. Such a
 * file answers `RenderOnly` rather than being written on a guess.
 *
 * Every chunk is padded to an even length by a byte its own size does not count:
 * `WebpRecipe`'s first mistake to make once, made once here too.
 */
internal object AviRecipe : TailRecipeCarrier {

    override fun boxIn(tail: ByteArray): IntRange? = tail.ourChunk()

    override fun accepts(head: ByteArray): Boolean =
        head.size >= 12 && head.matchesAt(0, RIFF) && head.matchesAt(8, AVI)

    override fun fits(head: ByteArray, length: Long): Boolean =
        accepts(head) && head.le32(SIZE_AT.toInt()) + RIFF_PREFIX == length

    override fun lengthPatch(length: Long): LengthPatch = LengthPatch(SIZE_AT, le32Bytes(length - RIFF_PREFIX))

    override fun scan(bytes: ByteArray): RecipeScan {
        val chunk = bytes.ourChunk() ?: return RecipeScan(null)
        val size = bytes.le32(chunk.first + 4).toInt()
        val from = chunk.first + OVERHEAD
        return RecipeScan(String(bytes, from, size - RECIPE_IDENTIFIER.size, Charsets.UTF_8))
    }

    override fun without(bytes: ByteArray): ByteArray? =
        bytes.ourChunk()?.let { bytes.removing(it).sized() }

    override fun box(payload: String): ByteArray? {
        val body = payload.toByteArray(Charsets.UTF_8)
        val size = RECIPE_IDENTIFIER.size + body.size
        if (HEADER + size + 1 > TAIL_WINDOW) return null
        return ByteArray(HEADER + size + (size and 1)).also { chunk ->
            OURS.copyInto(chunk)
            le32Bytes(size.toLong()).copyInto(chunk, 4)
            RECIPE_IDENTIFIER.copyInto(chunk, HEADER)
            body.copyInto(chunk, OVERHEAD)
        }
    }

    override fun with(bytes: ByteArray, payload: String): ByteArray? {
        if (!fits(bytes, bytes.size.toLong())) return null
        return box(payload)?.let { bytes.appending(it).sized() }
    }
}

/** Found by its identifier, confirmed by our chunk id and a size that fits, pad byte included. */
private fun ByteArray.ourChunk(): IntRange? {
    var from = 0
    while (true) {
        val found = identifierAt(from) ?: return null
        val at = found - HEADER
        if (at >= 0 && matchesAt(at, OURS)) {
            val size = le32(at + 4)
            val end = at + HEADER + size + (size and 1)
            if (size >= RECIPE_IDENTIFIER.size && end <= this.size) return at until end.toInt()
        }
        from = found + 1
    }
}

/** The `RIFF` size counts everything after itself, so it is rewritten last, always. */
private fun ByteArray.sized(): ByteArray =
    also { le32Bytes(it.size - RIFF_PREFIX).copyInto(it, SIZE_AT.toInt()) }
