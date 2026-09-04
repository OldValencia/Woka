package com.woka.feature.editor.recipe

import java.io.File

/**
 * One container's answer to "where does a recipe go in a file of this kind".
 *
 * Everything here works on **bytes, not files**. The reason is testability: a
 * carrier is byte surgery and nothing else, so every case — a truncated file, a
 * segment that lies about its length, a second write over a first — is an array
 * literal in a unit test rather than a temp directory. `EditRecipe` is the only
 * thing that opens anything.
 *
 * **Nothing here throws.** A malformed container reads as "no recipe" and refuses
 * to be written to; it never propagates. `ExifSplice` holds the same line for the
 * same reason: losing an edit is a far smaller harm than refusing to open a
 * picture.
 */
internal interface RecipeCarrier {

    /**
     * Which end of the file the recipe lives at (ME-108).
     *
     * A JPEG segment and a PNG chunk sit before the picture data, so the head answers
     * for both. An ISOBMFF `uuid` box is **appended after the last box** — HEIF item
     * offsets in `iloc` are absolute, so inserting earlier rewrites the meaning of
     * every one of them — and a head read would report every edited HEIC as untouched.
     */
    val window: RecipeWindow get() = RecipeWindow.Head

    /** What [bytes] carries, and whether the walk ran out of data before deciding. */
    fun scan(bytes: ByteArray): RecipeScan

    /**
     * [bytes] without our segment, or **null** when there is not one to remove —
     * which is what lets `clear` skip the write entirely rather than rewriting a
     * file to change nothing about it.
     */
    fun without(bytes: ByteArray): ByteArray?

    /**
     * [bytes] with [payload] inserted, or **null** when the container cannot hold
     * a payload that size. Assumes [without] has already run: it inserts, it does
     * not replace, so calling it twice would leave two segments.
     */
    fun with(bytes: ByteArray, payload: String): ByteArray?
}

/** Which slice of a file [EditRecipe] hands a carrier's [RecipeCarrier.scan]. */
internal enum class RecipeWindow { Head, Tail }

/**
 * A carrier whose slot is the **last thing in the file**, and can therefore be written
 * without copying it (ME-280).
 *
 * `EditRecipe.write` used to read the whole file, splice, and hand the array to
 * `writeAtomically`. For a 4 GB video that is an `OutOfMemoryError` *and* 4 GB of writes
 * to change three hundred bytes; with these three members it becomes a `setLength` back
 * over the old box and an append, O(payload).
 *
 * **Why giving up atomicity is correct here and nowhere else.** Our box sits after every
 * media box in the file, so nothing before the append is read, moved or rewritten: a
 * write that dies halfway leaves the picture data byte-identical and, at worst, a
 * truncated trailing box — which this interface's parent already reads as "no recipe",
 * because nothing here throws. A **head** carrier is the opposite: a JPEG's segment sits
 * *before* the image data and a half-written head is a destroyed photograph. Head
 * carriers therefore keep `writeAtomically`, and this is not a general relaxation of that
 * rule.
 *
 * Still bytes, never files — [RecipeCarrier]'s own contract — so the seeking lives beside
 * `EditRecipe` in `RecipeInPlace.kt` and every case here is an array literal in a test.
 */
internal interface TailRecipeCarrier : RecipeCarrier {

    override val window: RecipeWindow get() = RecipeWindow.Tail

    /**
     * Where our box sits inside [tail], or null when there is not one.
     *
     * A range rather than a start, because the caller has to know whether the box ends
     * where the **file** does: one that does not is a box with somebody else's bytes
     * after it, and truncating to its start would delete their data.
     */
    fun boxIn(tail: ByteArray): IntRange?

    /** Whether [head] is a container this carrier can extend at all. */
    fun accepts(head: ByteArray): Boolean

    /**
     * The bytes to append, or null when [payload] is larger than the window a reader
     * reads — the same refusal [with] makes, from the same code, so the two paths cannot
     * build a different box.
     */
    fun box(payload: String): ByteArray?
}

/**
 * [truncated] is what lets a reader work from the head of a file instead of all of
 * it. A walk that reaches the end of its window without finding either a recipe or
 * the start of the picture data has not answered the question — it ran out — and
 * the caller reads the rest. A walk that reached the image data has answered it.
 */
internal class RecipeScan(
    val payload: String?,
    val truncated: Boolean = false
)

/**
 * Which carrier reads which extension — the only place that knows.
 *
 * Shaped on `stillDecoderFor` in `feature/viewer/image/StillDecoders.kt`, and for
 * the same reason it was extracted: six copies of an extension list disagreed with
 * each other. **Null is bucket B or C** (§5.1), never an error.
 *
 * `bmp` is now the only format with no slot at all — every other extension Woka lists
 * carries its own edit, so bucket C is one format wide.
 */
internal fun carrierFor(file: File): RecipeCarrier? =
    when (file.extension.lowercase()) {
        // Both spellings are one format; the file keeps whichever it arrived with.
        "jpg", "jpeg" -> JpegRecipe
        "png" -> PngRecipe
        // One container family, three brands in its `ftyp` (ME-108).
        "heic", "heif", "avif" -> IsoBmffRecipe
        // Extended by us when it arrives simple (ME-137) — the last of bucket B.
        "webp" -> WebpRecipe
        // A private tag in a rebuilt IFD0, both spellings, every page (ME-139).
        "tif", "tiff" -> TiffRecipe
        // An Application Extension in the head of the stream, one recipe for every
        // frame of the animation (ME-138).
        "gif" -> GifRecipe
        else -> null
    }
