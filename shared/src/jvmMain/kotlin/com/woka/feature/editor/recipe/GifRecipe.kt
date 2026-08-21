package com.woka.feature.editor.recipe

/**
 * A recipe inside a GIF's own Application Extension block (ME-138).
 *
 * `0x21 0xFF 0x0B` + eleven bytes of identity + data sub-blocks + `0x00`. This is the
 * mechanism the Netscape looping extension uses, which is why every decoder in
 * existence already skips the ones it does not know — including skia, which is what
 * draws these frames and what reads the loop count out of exactly such a block.
 *
 * **One recipe, every frame.** Nothing here is per-frame and nothing needs to be:
 * `RecipeStillEdits` folds the file's one document over whatever bitmap a reader
 * handed it, so the viewer's clock, the strip's thumbnails, the grid tile and
 * `Export Frames` all draw the same turn (ME-133). An animation edited here plays
 * edited, which is what waiting for the clock bought (see `animated-stills`).
 *
 * **Written after any leading application extensions, not immediately after the Global
 * Color Table.** The ticket said the latter; the corpus said otherwise. `anim.gif` puts
 * `NETSCAPE2.0` in the very first position, and decoders exist that honour the loop
 * count only when it sits there — so ours goes after that run rather than in front of
 * it. Extensions are legal anywhere in the data stream and this is still the head of
 * the file, which is all the head window needs.
 *
 * **`GIF87a` is refused a write.** The Application Extension does not exist in that
 * version, and promoting the header would change the file's own claim about itself.
 * Such a file stays in bucket C, which is where it already was.
 */
internal object GifRecipe : RecipeCarrier {

    override fun scan(bytes: ByteArray): RecipeScan {
        val walk = bytes.gifWalk() ?: return RecipeScan(null)
        val ours = walk.ours ?: return RecipeScan(null, walk.truncated)
        // Past the introducer, the label, and the length byte and eleven bytes of the
        // identifier sub-block: what is left is the payload's own chain.
        val data = bytes.gifSubBlockData(ours.at + 3 + GIF_IDENTIFIER_SIZE)
        return RecipeScan(String(data, Charsets.UTF_8))
    }

    override fun without(bytes: ByteArray): ByteArray? {
        val ours = bytes.gifWalk()?.ours ?: return null
        val kept = ByteArray(bytes.size - (ours.end - ours.at))
        bytes.copyInto(kept, 0, 0, ours.at)
        bytes.copyInto(kept, ours.at, ours.end, bytes.size)
        return kept
    }

    override fun with(bytes: ByteArray, payload: String): ByteArray? {
        if (!bytes.gifMatches(0, "GIF89a")) return null
        val at = bytes.gifWalk()?.insertAt ?: return null
        val block = gifBlockOf(payload.toByteArray(Charsets.UTF_8))

        val out = ByteArray(bytes.size + block.size)
        bytes.copyInto(out, 0, 0, at)
        block.copyInto(out, at)
        bytes.copyInto(out, at + block.size, at, bytes.size)
        return out
    }
}
