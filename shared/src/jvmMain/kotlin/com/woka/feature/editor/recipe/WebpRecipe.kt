package com.woka.feature.editor.recipe

/** Private, and nothing else writes it — the same argument PNG's `wkEd` makes. */
private const val OURS = "WKED"

private const val RIFF = "RIFF"
private const val WEBP = "WEBP"
private const val VP8X = "VP8X"

/** `RIFF` + the size field + `WEBP`, after which the chunks begin. */
private const val HEADER = 12

private const val CHUNK_HEADER = 8

/** `RIFF` plus the size field itself, which the size does not count. */
private const val RIFF_PREFIX = 8

/** Flags + reserved + canvas width−1 + canvas height−1, three bytes each. */
private const val VP8X_SIZE = 10

/** Bit 4 of the flags byte: the file has alpha somewhere in it. */
private const val ALPHA_FLAG = 0x10

/**
 * A recipe inside a WebP's own RIFF chunk (ME-137).
 *
 * **A simple file has to be promoted first.** `RIFF`/`WEBP` followed by a lone `VP8 `
 * or `VP8L` chunk is the *simple* form, and the format allows nothing beside that
 * chunk; ancillary chunks are legal only in the extended form, which is announced by a
 * `VP8X` carrying the canvas size. So writing into a simple file means building that
 * `VP8X` from the bitstream's own dimensions (`WebpDimensions`) and putting it first.
 * An animated file is already extended and only gets our chunk appended.
 *
 * **The two mistakes this file exists to make once.** Every chunk is padded to an even
 * length by a byte that its own size field does not count, and the `RIFF` size at
 * offset 4 counts everything after itself — so both are recomputed on every write and
 * every clear rather than adjusted.
 *
 * **A clear leaves the `VP8X` behind.** Removing it would mean proving no other chunk
 * in the file depends on it, and an extended WebP is a valid WebP that every reader
 * handles — so this is the one carrier whose clear is not byte-for-byte the original.
 * Said out loud because ME-107's promise is exactly that, and here it is "the picture,
 * exactly" rather than "the file, exactly".
 */
internal object WebpRecipe : RecipeCarrier {

    override fun scan(bytes: ByteArray): RecipeScan {
        val walk = bytes.riffWalk() ?: return RecipeScan(null)
        val ours = walk.chunks.firstOrNull { it.type == OURS }
            ?: return RecipeScan(null, walk.truncated)
        return RecipeScan(String(bytes, ours.at + CHUNK_HEADER, ours.size, Charsets.UTF_8))
    }

    override fun without(bytes: ByteArray): ByteArray? {
        val walk = bytes.riffWalk() ?: return null
        val ours = walk.chunks.firstOrNull { it.type == OURS } ?: return null
        val kept = ByteArray(bytes.size - (ours.end - ours.at))
        bytes.copyInto(kept, 0, 0, ours.at)
        bytes.copyInto(kept, ours.at, ours.end, bytes.size)
        return kept.alsoSized()
    }

    override fun with(bytes: ByteArray, payload: String): ByteArray? {
        val walk = bytes.riffWalk() ?: return null
        val promotion = walk.promotion(bytes) ?: return null
        val chunk = chunkOf(payload.toByteArray(Charsets.UTF_8))

        val out = ByteArray(bytes.size + promotion.size + chunk.size)
        bytes.copyInto(out, 0, 0, HEADER)
        promotion.copyInto(out, HEADER)
        bytes.copyInto(out, HEADER + promotion.size, HEADER, bytes.size)
        chunk.copyInto(out, bytes.size + promotion.size)
        return out.alsoSized()
    }
}

private class RiffChunk(val type: String, val at: Int, val size: Int) {
    /** Past the payload **and** its pad byte, which the size field does not count. */
    val end: Int get() = at + CHUNK_HEADER + size + (size and 1)
}

private class RiffWalk(val chunks: List<RiffChunk>, val truncated: Boolean)

/**
 * The `VP8X` a simple file needs before it may carry anything else, or an empty array
 * when the file is already extended.
 *
 * Null — refuse the write — when the first chunk is neither `VP8X` nor a bitstream this
 * can read a canvas out of. Guessing a canvas is how a picture ends up cropped by its
 * own container.
 */
private fun RiffWalk.promotion(bytes: ByteArray): ByteArray? {
    val first = chunks.firstOrNull() ?: return null
    if (first.type == VP8X) return ByteArray(0)

    val canvas = webpCanvasOf(bytes, first.type, first.at + CHUNK_HEADER, first.size) ?: return null
    val body = ByteArray(VP8X_SIZE)
    body[0] = if (canvas.hasAlpha) ALPHA_FLAG.toByte() else 0
    body.putLe24(4, canvas.width - 1)
    body.putLe24(7, canvas.height - 1)
    return chunkOf(body, VP8X)
}

private fun chunkOf(body: ByteArray, type: String = OURS): ByteArray {
    val padded = body.size + (body.size and 1)
    return ByteArray(CHUNK_HEADER + padded).also { chunk ->
        type.toByteArray(Charsets.US_ASCII).copyInto(chunk)
        chunk.putLe32(4, body.size)
        body.copyInto(chunk, CHUNK_HEADER)
    }
}

/**
 * Chunks from the end of the header to the end of the array.
 *
 * Null is "not a WebP at all" and refuses everything; [RiffWalk.truncated] is "the
 * window ran out", which is a caller's cue to read more (`EditRecipe`).
 */
private fun ByteArray.riffWalk(): RiffWalk? {
    if (size < HEADER || !matches(0, RIFF) || !matches(8, WEBP)) return null
    val chunks = mutableListOf<RiffChunk>()
    var at = HEADER
    while (true) {
        if (at + CHUNK_HEADER > size) return RiffWalk(chunks, truncated = at != size)
        val chunk = RiffChunk(String(this, at, 4, Charsets.US_ASCII), at, leInt(at + 4))
        if (chunk.size < 0 || chunk.end > size) return RiffWalk(chunks, truncated = true)
        chunks += chunk
        at = chunk.end
    }
}

/** The size field counts everything after itself, so it is written last, always. */
private fun ByteArray.alsoSized(): ByteArray = also { it.putLe32(4, it.size - RIFF_PREFIX) }

private fun ByteArray.matches(at: Int, text: String): Boolean =
    text.indices.all { this[at + it] == text[it].code.toByte() }

private fun ByteArray.leInt(at: Int): Int =
    (this[at].toInt() and 0xFF) or
        ((this[at + 1].toInt() and 0xFF) shl 8) or
        ((this[at + 2].toInt() and 0xFF) shl 16) or
        ((this[at + 3].toInt() and 0xFF) shl 24)

private fun ByteArray.putLe32(at: Int, value: Int) {
    for (byte in 0..3) this[at + byte] = ((value shr (8 * byte)) and 0xFF).toByte()
}

private fun ByteArray.putLe24(at: Int, value: Int) {
    for (byte in 0..2) this[at + byte] = ((value shr (8 * byte)) and 0xFF).toByte()
}
