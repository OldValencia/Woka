package com.woka.feature.viewer.image

import java.io.File
import java.io.RandomAccessFile

/** An ISOBMFF box: its four-character type and its payload, `[start, end)` of [IsoBytes]. */
internal data class HeifBox(val type: String, val start: Int, val end: Int)

/**
 * Big-endian reads over a byte array — the one shape every HEIF box is written in.
 * Unsigned 32-bit fields come back as `Int` because nothing Woka reads from a
 * `meta` box exceeds 2³¹; a value that did is a broken file, and fails loudly.
 */
internal class IsoBytes(val bytes: ByteArray) {

    fun u8(at: Int): Int = bytes[at].toInt() and 0xFF

    fun u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

    fun s16(at: Int): Int = u16(at).toShort().toInt()

    fun u32Long(at: Int): Long = (u16(at).toLong() shl 16) or u16(at + 2).toLong()

    fun u32(at: Int): Int {
        val value = u32Long(at)
        check(value <= Int.MAX_VALUE) { "32-bit field $value at $at does not fit" }
        return value.toInt()
    }

    /** An unsigned field of [size] bytes — `iloc` sizes its offsets per file (0, 4 or 8). */
    fun uN(at: Int, size: Int): Int = when (size) {
        0 -> 0
        2 -> u16(at)
        4 -> u32(at)
        8 -> {
            check(u32(at) == 0) { "64-bit offset at $at is past 2 GB" }
            u32(at + 4)
        }
        else -> error("field size $size")
    }

    fun fourCc(at: Int): String = String(bytes, at, 4, Charsets.ISO_8859_1)

    /** The boxes laid end to end in `[from, to)`; a box claiming to run past [to] is cut there. */
    fun boxes(from: Int, to: Int): List<HeifBox> {
        val found = mutableListOf<HeifBox>()
        var at = from
        while (at + 8 <= to) {
            val size = u32(at)
            check(size >= 8) { "box of size $size at $at" }
            found += HeifBox(fourCc(at + 4), at + 8, minOf(at + size, to))
            at += size
        }
        return found
    }
}

/**
 * The `meta` box of [file], read without touching anything after it.
 *
 * Only the top-level box headers are walked — `ftyp`, `meta`, `free`, `mdat` — so
 * asking a 40 MB Paint project for its size reads a few hundred bytes, not the
 * layers. 64-bit (`size == 1`) top-level boxes are honoured, since `mdat` is the
 * one box a large file writes that way.
 */
internal fun readHeifMetaBox(file: File): IsoBytes = RandomAccessFile(file, "r").use { input ->
    var at = 0L
    val header = ByteArray(16)
    while (at + 8 <= input.length()) {
        input.seek(at)
        input.readFully(header, 0, 8)
        val head = IsoBytes(header)
        val type = head.fourCc(4)
        var size = head.u32Long(0)
        if (size == 1L) {
            input.readFully(header, 8, 8)
            size = (head.u32Long(8) shl 32) or head.u32Long(12)
        }
        if (size == 0L) size = input.length() - at
        if (type == "meta") {
            val payload = ByteArray((size - 8).toInt())
            input.readFully(payload)
            return@use IsoBytes(payload)
        }
        at += size
    }
    error("${file.name} has no meta box")
}

/** [length] bytes of [file] at [offset] — one `iloc` extent. */
internal fun readFileRange(file: File, offset: Long, length: Int): ByteArray =
    RandomAccessFile(file, "r").use { input ->
        ByteArray(length).also {
            input.seek(offset)
            input.readFully(it)
        }
    }
