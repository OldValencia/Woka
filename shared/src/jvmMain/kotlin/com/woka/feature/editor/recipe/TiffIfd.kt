package com.woka.feature.editor.recipe

/** Little-endian files say `II`, big-endian ones `MM`, and both are common. */
internal class TiffOrder(val little: Boolean) {

    fun short(bytes: ByteArray, at: Int): Int = if (little) {
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
    } else {
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
    }

    fun int(bytes: ByteArray, at: Int): Int = if (little) {
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)
    } else {
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
    }

    fun putShort(bytes: ByteArray, at: Int, value: Int) {
        val high = ((value shr 8) and 0xFF).toByte()
        val low = (value and 0xFF).toByte()
        bytes[at] = if (little) low else high
        bytes[at + 1] = if (little) high else low
    }

    fun putInt(bytes: ByteArray, at: Int, value: Int) {
        for (byte in 0..3) {
            val shift = if (little) 8 * byte else 8 * (3 - byte)
            bytes[at + byte] = ((value shr shift) and 0xFF).toByte()
        }
    }
}

/** The 8-byte header: byte order, the `42` that says TIFF, and where IFD0 starts. */
internal class TiffHeader(val order: TiffOrder, val ifdAt: Int)

internal const val TIFF_HEADER = 8

internal const val ENTRY_SIZE = 12

/**
 * The header, or null for anything that is not a TIFF this can safely touch.
 *
 * `42` is the only magic accepted: `43` is BigTIFF, whose offsets are 64-bit and whose IFDs are a
 * different shape entirely. Refusing it leaves such a file exactly as it is rather than writing
 * something plausible into a layout this does not understand.
 */
internal fun tiffHeaderOf(bytes: ByteArray): TiffHeader? {
    if (bytes.size < TIFF_HEADER) return null
    val little = when {
        bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() -> true
        bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() -> false
        else -> return null
    }
    val order = TiffOrder(little)
    if (order.short(bytes, 2) != 42) return null
    val ifdAt = order.int(bytes, 4)
    if (ifdAt < TIFF_HEADER || ifdAt + 2 > bytes.size) return null
    return TiffHeader(order, ifdAt)
}

/**
 * IFD0 rebuilt with [entry] folded in, followed by the original link to the next IFD.
 *
 * **The link is carried over, not dropped**, which is what keeps every page of a multi-page TIFF
 * reachable: pages are a chain, and a rebuilt IFD0 that forgot its `next` would leave a three-page
 * scan showing one page.
 *
 * Entries stay in **ascending tag order**, which the specification requires and which some readers
 * rely on to binary-search. Any existing copy of our own tag is dropped first, so a rebuild is
 * idempotent rather than a way to accumulate duplicates.
 */
internal fun rebuiltIfd(bytes: ByteArray, header: TiffHeader, entry: ByteArray, tag: Int): ByteArray? {
    val order = header.order
    val count = order.short(bytes, header.ifdAt)
    val entriesAt = header.ifdAt + 2
    if (entriesAt + count * ENTRY_SIZE + 4 > bytes.size) return null

    val kept = (0 until count)
        .map { entriesAt + it * ENTRY_SIZE }
        .filter { order.short(bytes, it) != tag }
        .map { bytes.copyOfRange(it, it + ENTRY_SIZE) }
    val all = (kept + listOf(entry)).sortedBy { order.short(it, 0) }

    val out = ByteArray(2 + all.size * ENTRY_SIZE + 4)
    order.putShort(out, 0, all.size)
    all.forEachIndexed { at, one -> one.copyInto(out, 2 + at * ENTRY_SIZE) }
    // The original link, read from where it sits after the original entries.
    order.putInt(out, out.size - 4, order.int(bytes, entriesAt + count * ENTRY_SIZE))
    return out
}
