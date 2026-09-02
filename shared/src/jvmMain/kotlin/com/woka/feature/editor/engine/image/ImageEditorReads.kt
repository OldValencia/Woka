package com.woka.feature.editor.engine.image

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.isAnimation
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.viewer.image.stillDecoderFor
import java.io.File

/**
 * The three questions the image engine asks a file before anything has been edited:
 * how large it is, what is inside it, and what it already carries.
 *
 * Split out of `ImageEditorEngine` when ME-156 added the second of them and pushed
 * that file past the size mandate. Every one of them is a read of a path — no engine
 * state, no document — which is why they were the cut and why the engine keeps only
 * the `by lazy` that decides *when* each is asked.
 *
 * **All three are lazy at the call site, and that is load-bearing.** The factory
 * decides editability from the format alone and must not touch the disk to grey out a
 * menu item.
 */

/**
 * From the **decoder** rather than from any bitmap: the viewer may hold a picture
 * capped below native while every zoom percentage is defined against native 1:1
 * pixels, and `StillDecoder.nativeSize` answers without decoding.
 */
internal fun nativeFrameSize(file: File, frameIndex: Int): IntSize =
    stillDecoderFor(file).nativeSize(file, frameIndex)

/**
 * What the shell has to know about a file holding more than one picture (ME-156).
 *
 * `count` and `playsAsAnimation` come from the decoder's viewable frames — the two
 * the viewer's own strip already uses — and the scope and the marks from the stored
 * payload. **Neither read throws**: a decoder that cannot open the container and a
 * malformed payload both answer "one picture, nothing overridden", which draws no
 * strip rather than refusing to open the editor.
 */
internal fun editorFramesOf(file: File, frameIndex: Int): EditorFrames {
    val viewable = runCatching { stillDecoderFor(file).frameSet(file).viewable }
        .getOrDefault(emptyList())
    val payload = runCatching { EditRecipe.read(file) }.getOrNull()
    return EditorFrames(
        count = viewable.size.coerceAtLeast(1),
        playsAsAnimation = viewable.isAnimation,
        storedScope = payload?.scopeOf(frameIndex) ?: EditScope.AllFrames,
        overridden = payload?.frames?.keys.orEmpty()
    )
}

/**
 * What the file carries for this frame, so a second sitting continues the first
 * (ME-134, keyed by ME-126).
 *
 * The canvas is corrected by seeding the session and **not** by folding this into
 * the proxy: `EditorProxy` reads raw pixels by design (ME-133) and the session folds
 * the document over them, so applying it here as well would draw two rotations for
 * one.
 */
internal fun storedDocumentIn(file: File, frameIndex: Int): EditDocument =
    EditRecipe.documentIn(file, frameIndex) ?: EditDocument()

/**
 * The file-wide layer alone — `EditPayload.ops`, without any frame's own work folded on
 * (ME-175's base, read back).
 *
 * The session needs the two apart to tell a frame's *delta* from the composition it is
 * shown as: arriving at a frame carrying its own stored crop, with unsaved shared work
 * in hand, means base-plus-that-crop, and neither half can be recovered from the
 * composed document alone.
 */
internal fun storedBaseIn(file: File): EditDocument =
    EditRecipe.read(file)?.let { EditDocument(it.ops) } ?: EditDocument()
