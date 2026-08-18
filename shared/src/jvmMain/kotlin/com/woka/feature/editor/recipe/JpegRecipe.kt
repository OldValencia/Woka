package com.woka.feature.editor.recipe

private const val MARKER = 0xFF.toByte()
private const val SOI = 0xD8.toByte()
private const val SOS = 0xDA.toByte()

/** `APP8`: the SPIFF leftover nobody writes, and the least likely to collide. */
private const val OURS = 0xE8.toByte()

private const val APP_FIRST = 0xE0
private const val APP_LAST = 0xEF

/** Six bytes, shaped like `Exif\u0000\u0000` — the marker is not what identifies us. */
private val IDENTIFIER = "Woka\u0000\u0000".toByteArray(Charsets.US_ASCII)

/** A segment's length field is 16 bits and counts itself. */
private const val MAX_LENGTH = 0xFFFF

/**
 * A recipe inside a JPEG's own private `APP8` segment.
 *
 * **The identifier does the protecting, not the marker.** Any `APPn` may hold
 * anybody's data, so a segment is ours only when its payload begins [IDENTIFIER];
 * one that does not is left exactly where it is.
 *
 * **Inserted after the leading `APPn` run**, not immediately after `SOI`. A strict
 * EXIF reader looks at the first segment, and displacing `APP1` to make room would
 * be a change to the file's metadata made for no reason at all.
 */
internal object JpegRecipe : RecipeCarrier {

    override fun scan(bytes: ByteArray): RecipeScan {
        val walk = bytes.jpegWalk()
        val ours = walk.segments.firstOrNull { it.isOurs(bytes) }
            ?: return RecipeScan(null, walk.truncated)
        val from = ours.at + 4 + IDENTIFIER.size
        return RecipeScan(String(bytes, from, ours.end - from, Charsets.UTF_8))
    }

    override fun without(bytes: ByteArray): ByteArray? {
        val ours = bytes.jpegWalk().segments.filter { it.isOurs(bytes) }
        if (ours.isEmpty()) return null
        val kept = ByteArray(bytes.size - ours.sumOf { it.end - it.at })
        var written = 0
        var from = 0
        ours.forEach { segment ->
            bytes.copyInto(kept, written, from, segment.at)
            written += segment.at - from
            from = segment.end
        }
        bytes.copyInto(kept, written, from, bytes.size)
        return kept
    }

    override fun with(bytes: ByteArray, payload: String): ByteArray? {
        val body = payload.toByteArray(Charsets.UTF_8)
        val length = 2 + IDENTIFIER.size + body.size
        if (length > MAX_LENGTH) return null

        val walk = bytes.jpegWalk()
        if (!walk.valid) return null
        val at = walk.insertAt

        val segment = ByteArray(2 + length)
        segment[0] = MARKER
        segment[1] = OURS
        segment[2] = ((length shr 8) and 0xFF).toByte()
        segment[3] = (length and 0xFF).toByte()
        IDENTIFIER.copyInto(segment, 4)
        body.copyInto(segment, 4 + IDENTIFIER.size)

        return ByteArray(bytes.size + segment.size).also { out ->
            bytes.copyInto(out, 0, 0, at)
            segment.copyInto(out, at)
            bytes.copyInto(out, at + segment.size, at)
        }
    }
}

private class Segment(val marker: Byte, val at: Int, val end: Int) {

    fun isOurs(bytes: ByteArray): Boolean {
        if (marker != OURS || end - at < 4 + IDENTIFIER.size) return false
        return IDENTIFIER.indices.all { bytes[at + 4 + it] == IDENTIFIER[it] }
    }
}

private class JpegWalk(
    val segments: List<Segment>,
    /** Where a new segment goes: the end of the leading `APPn` run. */
    val insertAt: Int,
    val truncated: Boolean,
    val valid: Boolean
)

/**
 * Every segment from `SOI` up to the scan header, which is where segments end and
 * image data begins.
 *
 * Truncation is reported rather than treated as corruption: a caller reading only
 * the head of a large file will hit it legitimately, and the answer is "read more",
 * not "this file is broken".
 */
private fun ByteArray.jpegWalk(): JpegWalk {
    if (size < 4 || this[0] != MARKER || this[1] != SOI) {
        return JpegWalk(emptyList(), 0, truncated = false, valid = false)
    }
    val segments = mutableListOf<Segment>()
    var insertAt = -1
    var at = 2

    fun stop(cut: Boolean) = JpegWalk(segments, insertAt.orElse(at), cut, valid = true)

    while (true) {
        if (at + 4 > size) return stop(cut = true)
        if (this[at] != MARKER) return stop(cut = false)

        val marker = this[at + 1]
        if (marker == SOS) return stop(cut = false)

        val length = ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)
        if (length < 2) return stop(cut = false)

        val end = at + 2 + length
        if (end > size) return stop(cut = true)

        if (insertAt < 0 && (marker.toInt() and 0xFF) !in APP_FIRST..APP_LAST) insertAt = at
        segments += Segment(marker, at, end)
        at = end
    }
}

private fun Int.orElse(fallback: Int): Int = if (this < 0) fallback else this
