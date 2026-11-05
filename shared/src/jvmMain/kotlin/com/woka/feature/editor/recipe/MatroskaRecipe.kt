package com.woka.feature.editor.recipe

/**
 * Ours: a 4-byte class-D id (first byte in `0x10…0x1F`) reading `WkE` in a hex dump. It
 * collides with no assigned top-level id — EBML, Segment, SeekHead, Info, Cluster, Tracks,
 * Cues, Attachments, Chapters, Tags.
 */
private val ELEMENT_ID = byteArrayOf(0x1E, 0x57, 0x6B, 0x45)

private val EBML_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

private val SEGMENT_ID = byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67)

/** The id, then a fixed-width 8-byte VINT: marker `0x01` and seven big-endian bytes. */
private const val HEADER = 12

private const val OVERHEAD = HEADER + 16

/**
 * A recipe in a top-level EBML element appended after the `Segment` — `mkv`, `webm`
 * (ME-282).
 *
 * **Measured on the corpus, and it settles the shape.** Every file's `Segment` declares a
 * known size and ends exactly at EOF, so bytes appended after it are a *new top-level
 * element*: nothing moves and no size field is rewritten. That makes this a tail carrier,
 * and ME-280's `setLength`-and-append comes for nothing.
 *
 * **The two mistakes to make once.** A *streamed* Matroska writes its `Segment` size as
 * all ones — "to the end of the file" — and then anything appended is *inside* it as far
 * as a reader is concerned; [accepts] refuses that file rather than guess. And the length
 * VINT is written at a fixed eight bytes rather than the shortest legal width, so the
 * header is a constant [HEADER] and [boxIn] is arithmetic rather than a second decoder.
 *
 * The id is ours but self-assigned, so the identifier inside the payload does the
 * protecting, exactly as a `uuid` box's does: an element with our id and somebody else's
 * bytes is neither read nor removed.
 */
internal object MatroskaRecipe : TailRecipeCarrier {

    override fun boxIn(tail: ByteArray): IntRange? = tail.ourElement()

    override fun accepts(head: ByteArray): Boolean = head.segmentEnd() != null

    /**
     * The `Segment` must end inside the file. An interrupted recording or download declares
     * more than it holds, and an element appended there lands *inside* the Segment — inside
     * its last partial block, which a demuxer then reads as a frame (1.0.6 review).
     */
    override fun fits(head: ByteArray, length: Long): Boolean = head.segmentEnd()?.let { it <= length } == true

    override fun scan(bytes: ByteArray): RecipeScan {
        val element = bytes.ourElement() ?: return RecipeScan(null)
        val from = element.first + OVERHEAD
        return RecipeScan(String(bytes, from, element.last + 1 - from, Charsets.UTF_8))
    }

    override fun without(bytes: ByteArray): ByteArray? = bytes.ourElement()?.let { bytes.removing(it) }

    override fun box(payload: String): ByteArray? {
        val body = payload.toByteArray(Charsets.UTF_8)
        if (body.size + OVERHEAD > TAIL_WINDOW) return null
        val length = (RECIPE_IDENTIFIER.size + body.size).toLong()
        return ByteArray(OVERHEAD + body.size).also { element ->
            ELEMENT_ID.copyInto(element)
            element[4] = 0x01
            for (byte in 0 until 7) element[5 + byte] = ((length shr (8 * (6 - byte))) and 0xFF).toByte()
            RECIPE_IDENTIFIER.copyInto(element, HEADER)
            body.copyInto(element, OVERHEAD)
        }
    }

    override fun with(bytes: ByteArray, payload: String): ByteArray? {
        if (!accepts(bytes) || !fits(bytes, bytes.size.toLong())) return null
        return box(payload)?.let { bytes.appending(it) }
    }
}

/** Found by its identifier, confirmed by our id and a length that fits — `IsoBmffRecipe`'s search. */
private fun ByteArray.ourElement(): IntRange? {
    var from = 0
    while (true) {
        val found = identifierAt(from) ?: return null
        val at = found - HEADER
        if (at >= 0 && matchesAt(at, ELEMENT_ID) && this[at + 4] == 0x01.toByte()) {
            val length = bigEndian(at + 5, 7)
            if (length >= RECIPE_IDENTIFIER.size && length <= size - at - HEADER) {
                return at until at + HEADER + length.toInt()
            }
        }
        from = found + 1
    }
}

/**
 * Where the `Segment` ends: `1a 45 df a3`, the EBML header skipped by its own size, then a
 * `Segment` not of unknown size. Null for anything else.
 */
private fun ByteArray.segmentEnd(): Long? {
    if (!matchesAt(0, EBML_MAGIC)) return null
    val header = vintAt(EBML_MAGIC.size) ?: return null
    val segment = EBML_MAGIC.size + header.width + header.value
    if (segment > size - SEGMENT_ID.size || !matchesAt(segment.toInt(), SEGMENT_ID)) return null
    val length = vintAt(segment.toInt() + SEGMENT_ID.size) ?: return null
    if (length.unknown) return null
    return segment + SEGMENT_ID.size + length.width + length.value
}

private class Vint(val width: Int, val value: Long, val unknown: Boolean)

/** The leading zeros of the first byte give the width; every value bit set means "unknown". */
private fun ByteArray.vintAt(at: Int): Vint? {
    if (at >= size) return null
    val first = this[at].toInt() and 0xFF
    if (first == 0) return null
    val width = Integer.numberOfLeadingZeros(first) - 23
    if (at + width > size) return null
    var value = (first and (0xFF shr width)).toLong()
    for (byte in 1 until width) value = (value shl 8) or (this[at + byte].toLong() and 0xFF)
    return Vint(width, value, unknown = value == (1L shl (7 * width)) - 1)
}

private fun ByteArray.bigEndian(at: Int, count: Int): Long {
    var value = 0L
    for (byte in 0 until count) value = (value shl 8) or (this[at + byte].toLong() and 0xFF)
    return value
}
