package com.woka.feature.editor.recipe

import java.io.File
import java.io.RandomAccessFile

/**
 * How much of a file has to be read to answer "does this carry a recipe", and how.
 *
 * Split out of `EditRecipe` when ME-126 pushed that file past the size mandate. It is
 * the natural seam: everything here is about *bytes off a disk* and nothing here knows
 * what a document is, while `EditRecipe` decides what those bytes mean.
 */

/**
 * How large a slice of a file is enough to answer "does this carry a recipe".
 *
 * The head carriers stop walking where the picture data begins, so for a file either
 * of them wrote the answer is in the first few kilobytes. The window exists for the
 * pathological case — a JPEG whose leading `APPn` run holds a large ICC profile —
 * and `EditRecipe.read` falls back to the whole file only when a walk actually ran out.
 *
 * This matters because the grid asks the question per visible tile (ME-133).
 * Reading a 5 MB photograph to find out it has never been edited is the difference
 * between a scroll and a stutter.
 */
private const val HEAD_BYTES = 512 * 1024

/**
 * The same question from the other end (ME-108), and far smaller: a tail carrier's box
 * is the **last** thing in the file, so a slice this size holds it and its header with
 * room to spare. Only a file rewritten by another program — appending something of its
 * own after ours — needs more, and that falls back to the whole file like any other
 * walk that ran out.
 */
internal const val TAIL_WINDOW = 64 * 1024

internal fun File.slice(window: RecipeWindow): ByteArray? = when (window) {
    RecipeWindow.Head -> head()
    RecipeWindow.Tail -> tail()
}

private fun File.head(): ByteArray? = runCatching {
    inputStream().use { stream -> stream.readNBytes(minOf(length(), HEAD_BYTES.toLong()).toInt()) }
}.getOrNull()

/**
 * The last [TAIL_WINDOW] bytes of the file, or all of it when it is smaller.
 *
 * A `RandomAccessFile` rather than a skip-and-read, because skipping is not a seek on
 * every stream and this runs per visible grid tile.
 */
private fun File.tail(): ByteArray? = runCatching {
    val size = length()
    val from = maxOf(0L, size - TAIL_WINDOW)
    RandomAccessFile(this, "r").use { handle ->
        handle.seek(from)
        ByteArray((size - from).toInt()).also { handle.readFully(it) }
    }
}.getOrNull()
