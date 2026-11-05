package com.woka.feature.editor.recipe

import java.io.File

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

    /**
     * Whether *this* file, [length] bytes long, can be extended — past the magic [accepts]
     * reads. A RIFF must end where the file does, or the box would land outside it
     * (ME-283); ISOBMFF and Matroska have nothing more to ask.
     */
    fun fits(head: ByteArray, length: Long): Boolean = true

    /**
     * The same question asked of the whole [file], at the write only — for a container whose
     * answer is in its box layout rather than its head (`isoBoxesEndCleanly`, 1.0.6 review).
     */
    fun fitsFile(file: File): Boolean = true

    /**
     * The header field that counts the file's own length, rewritten for a file [length]
     * bytes long — or null for a container that keeps none (ISOBMFF, Matroska). RIFF's
     * size at offset 4 is the one there is (ME-283).
     */
    fun lengthPatch(length: Long): LengthPatch? = null
}

/**
 * Four bytes at [at], written after an append and before a truncate — so a write that
 * dies between the two leaves a size that is merely short, which every reader tolerates.
 */
internal class LengthPatch(val at: Long, val bytes: ByteArray)

/**
 * Whether [file] answers `Recorded` rather than `RenderOnly` (ME-289): a carrier for its
 * extension, and — for a tail carrier — one that accepts this file's head and length. A
 * streamed Matroska or an OpenDML `avi` is refused at the door, not at `Done`.
 */
internal fun carriesHere(file: File): Boolean {
    val carrier = carrierFor(file) ?: return false
    if (carrier !is TailRecipeCarrier) return true
    val head = file.leading(HEAD_PROBE) ?: return false
    return carrier.accepts(head) && carrier.fits(head, file.length())
}
