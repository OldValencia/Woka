package com.woka.feature.editor.recipe

/**
 * Twelve bytes that mark our block, and **never change**: the tail read finds the block by this
 * rather than by following an offset it cannot resolve inside a slice.
 */
private val IDENTIFIER = "WokaEditRec\u0000".toByteArray(Charsets.US_ASCII)

/** Private tags start at 32768; this one is ours and is written into IFD0. */
private const val TAG = 0xA057

/** `UNDEFINED` — an arbitrary run of bytes, which is what JSON is to TIFF. */
private const val TYPE_UNDEFINED = 7

/** Identifier + the original IFD0 offset + the payload length, before the payload itself. */
private const val BLOCK_HEADER = 12 + 4 + 4

/**
 * A recipe inside a TIFF's own private IFD tag (ME-139).
 *
 * **Nothing in the original file moves.** An entry is 12 bytes and adding one to an IFD in place
 * would shift every byte after it, while every value in a TIFF is addressed by an absolute file
 * offset — so the picture would still be there and nothing would be able to find it. Instead the
 * payload and a **rebuilt IFD0** are appended, and the header's four-byte pointer is repointed at
 * the copy. The original IFD0 stays exactly where it was, unread.
 *
 * **The block carries the offset it replaced**, which is what makes a clear byte-identical: put the
 * old pointer back, cut the file at the start of the block, and the file is the file again. ME-107
 * promises exactly that and `RevertTest` measures it.
 *
 * **Our own two fields are big-endian regardless of the file**, because a tail slice may not contain
 * the header that says which order the file uses. The IFD entry beside them is written in the file's
 * order, since that one is read by TIFF readers rather than by us.
 *
 * **IFD0 only.** Later pages are a chain hanging off it and are left untouched; the recipe belongs to
 * the file (ME-126), so one tag says it for every page.
 */
internal object TiffRecipe : RecipeCarrier {

    override val window = RecipeWindow.Tail

    override fun scan(bytes: ByteArray): RecipeScan {
        val at = bytes.indexOfIdentifier() ?: return RecipeScan(null)
        val length = bytes.beInt(at + 16)
        val from = at + BLOCK_HEADER
        if (length < 0 || from + length > bytes.size) return RecipeScan(null)
        return RecipeScan(String(bytes, from, length, Charsets.UTF_8))
    }

    override fun without(bytes: ByteArray): ByteArray? {
        val at = bytes.indexOfIdentifier() ?: return null
        val header = tiffHeaderOf(bytes) ?: return null
        val restored = bytes.copyOfRange(0, at)
        header.order.putInt(restored, 4, bytes.beInt(at + 12))
        return restored
    }

    override fun with(bytes: ByteArray, payload: String): ByteArray? {
        val header = tiffHeaderOf(bytes) ?: return null
        val body = payload.toByteArray(Charsets.UTF_8)
        // The block starts **exactly** at the end of the original file and nothing is written in
        // front of it — not even a pad byte — because a clear cuts the file here, and one stray byte
        // is the difference between "the file is the file again" and "near enough". The alignment a
        // TIFF offset wants is taken inside the block instead, before the rebuilt IFD.
        val blockAt = bytes.size
        val payloadAt = blockAt + BLOCK_HEADER
        val ifdAt = payloadAt + body.size + ((payloadAt + body.size) and 1)

        val entry = ByteArray(ENTRY_SIZE).also {
            header.order.putShort(it, 0, TAG)
            header.order.putShort(it, 2, TYPE_UNDEFINED)
            header.order.putInt(it, 4, body.size)
            header.order.putInt(it, 8, payloadAt)
        }
        val ifd = rebuiltIfd(bytes, header, entry, TAG) ?: return null

        val out = ByteArray(ifdAt + ifd.size)
        bytes.copyInto(out)
        IDENTIFIER.copyInto(out, blockAt)
        out.putBeInt(blockAt + 12, header.ifdAt)
        out.putBeInt(blockAt + 16, body.size)
        body.copyInto(out, payloadAt)
        ifd.copyInto(out, ifdAt)
        header.order.putInt(out, 4, ifdAt)
        return out
    }
}

/**
 * Ours, found by the identifier rather than by walking to it.
 *
 * A tail slice starts in the middle of the picture data, so an absolute offset out of the header
 * means nothing inside it — the same reason ME-108's box is searched for. Twelve matching bytes
 * followed by a length that fits is not something a compressed scan produces by accident.
 */
private fun ByteArray.indexOfIdentifier(): Int? {
    for (at in 0..size - BLOCK_HEADER) {
        if (IDENTIFIER.indices.all { this[at + it] == IDENTIFIER[it] }) return at
    }
    return null
}

private fun ByteArray.beInt(at: Int): Int =
    ((this[at].toInt() and 0xFF) shl 24) or
        ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or
        (this[at + 3].toInt() and 0xFF)

private fun ByteArray.putBeInt(at: Int, value: Int) {
    for (byte in 0..3) this[at + byte] = ((value shr (8 * (3 - byte))) and 0xFF).toByte()
}
