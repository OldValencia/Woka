package com.woka.feature.editor.save

import java.io.File

private const val MARKER = 0xFF.toByte()
private const val SOI = 0xD8.toByte()
private const val APP1 = 0xE1.toByte()
private const val ORIENTATION_TAG = 0x0112

/**
 * Puts the source's EXIF back into a freshly encoded JPEG, with `Orientation` set to 1.
 *
 * **Two measured facts make this file necessary, and both are easy to get backwards.**
 *
 * A Skia JPEG re-encode drops `APP1` entirely (ME-010), so camera, lens, date and GPS
 * are gone unless something deliberately carries them over — §6's baseline is
 * *preserve*, and this is the only thing implementing it.
 *
 * And the decode path **already applied** `Orientation` (ME-010): a 640×480 source
 * tagged `6` arrives 480×640, so the rotation is in the pixels being saved. Copying
 * the tag across unchanged would tell the next reader to rotate again. The tag is
 * therefore rewritten to 1 in place — one 16-bit field, leaving every other entry and
 * every offset exactly where it was, because rebuilding the IFD would mean rebuilding
 * the offsets inside it.
 *
 * **Never throws.** A malformed segment, a source that is not a JPEG, an absent
 * `APP1` — every one returns the encoded bytes unchanged. Losing metadata is a far
 * smaller harm than losing the save, and §6 states that fallback openly.
 */
internal fun spliceExif(encoded: ByteArray, source: File): ByteArray = runCatching {
    if (!encoded.startsWithSoi()) return@runCatching encoded
    val app1 = source.readApp1() ?: return@runCatching encoded

    // After SOI and before everything else, which is where a reader expects it.
    ByteArray(encoded.size + app1.size).also { out ->
        encoded.copyInto(out, 0, 0, 2)
        app1.copyInto(out, 2)
        encoded.copyInto(out, 2 + app1.size, 2)
    }
}.getOrDefault(encoded)

private fun ByteArray.startsWithSoi(): Boolean =
    size > 2 && this[0] == MARKER && this[1] == SOI

/**
 * The whole `APP1` segment — marker, length and payload — with `Orientation` forced
 * to 1, or null when there is not one to take.
 */
private fun File.readApp1(): ByteArray? {
    val bytes = runCatching { readBytes() }.getOrNull() ?: return null
    if (!bytes.startsWithSoi()) return null

    var at = 2
    while (at + 4 <= bytes.size && bytes[at] == MARKER) {
        val marker = bytes[at + 1]
        val length = ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)
        if (length < 2) return null
        val end = at + 2 + length
        if (end > bytes.size) return null

        if (marker == APP1) {
            return bytes.copyOfRange(at, end).also { it.neutraliseOrientation() }
        }
        // Anything past the first scan header is image data, not segments.
        if (marker == 0xDA.toByte()) return null
        at = end
    }
    return null
}

/**
 * Rewrites IFD0's `Orientation` value to 1, in place, leaving the rest of the segment
 * byte-identical. Does nothing at all if the structure is not what it claims to be.
 */
private fun ByteArray.neutraliseOrientation() {
    // marker(2) + length(2) + "Exif\0\0"(6) = 10, then the TIFF header begins.
    val tiff = 10
    if (size < tiff + 8) return

    val bigEndian = this[tiff] == 0x4D.toByte() && this[tiff + 1] == 0x4D.toByte()
    val littleEndian = this[tiff] == 0x49.toByte() && this[tiff + 1] == 0x49.toByte()
    if (!bigEndian && !littleEndian) return

    fun u16(at: Int): Int = if (bigEndian) {
        ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)
    } else {
        ((this[at + 1].toInt() and 0xFF) shl 8) or (this[at].toInt() and 0xFF)
    }

    fun u32(at: Int): Int = if (bigEndian) {
        (u16(at) shl 16) or u16(at + 2)
    } else {
        (u16(at + 2) shl 16) or u16(at)
    }

    val ifd0 = tiff + u32(tiff + 4)
    if (ifd0 + 2 > size) return
    val entries = u16(ifd0)

    repeat(entries) { index ->
        val entry = ifd0 + 2 + index * 12
        if (entry + 12 > size) return
        if (u16(entry) == ORIENTATION_TAG) {
            // A SHORT's value sits in the first two bytes of the 4-byte value field.
            val value = entry + 8
            if (bigEndian) {
                this[value] = 0
                this[value + 1] = 1
            } else {
                this[value] = 1
                this[value + 1] = 0
            }
            return
        }
    }
}
