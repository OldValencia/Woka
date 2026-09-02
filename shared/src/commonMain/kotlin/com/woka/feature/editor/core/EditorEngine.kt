package com.woka.feature.editor.core

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind

/**
 * Everything about editing that knows what pixels are — behind one interface
 * (§3.3).
 *
 * `EditorSession`, the history, the tool contract, the schema-driven inspector and
 * the save banner all sit above this and none of them can name a bitmap. The image
 * engine folds to a raster recipe and draws a bitmap; the video engine folds to a
 * timeline edit and draws the player plus a trim strip, and its saver runs ffmpeg
 * with `-c copy`. The shell composes [Surface] and **never branches on media
 * kind** — that is what makes "the video editor inherits the shell" a fact the
 * compiler enforces rather than an intention.
 *
 * **The recipe type stays inside the implementation.** There is no `recipe()` here
 * on purpose: the moment the shell can name `RasterRecipe`, every file that
 * touches it becomes a video ticket later.
 *
 * What it *draws* is [EditorEngineSurface], extended rather than declared here since
 * ME-156 — the same interface, in two files, because this one reached the size
 * mandate.
 */
interface EditorEngine : EditorEngineSurface {

    val kind: MediaKind

    /**
     * What saving this file costs it (§5.1) — decided once, by the factory, and
     * carried here rather than re-derived.
     *
     * The engine branches its save on it and the shell draws bucket B's notice from
     * it, so the two cannot disagree about the same file. A `Unavailable` engine is
     * never built by the factory; the value exists so the branch is total.
     */
    val capability: EditCapability

    /** What operations address, in source pixels — never the canvas or the proxy. */
    val sourceSize: IntSize

    /**
     * What the picture *is*, for the one hook that needs it: `EditorTool.resolve`
     * (ME-166).
     *
     * Null by default and null forever for a medium with no still to measure, so the
     * video engine inherits this without a line. Null too until the proxy has arrived —
     * the shell never blocks on it, and nothing can ask before there is a picture on
     * screen to ask about.
     */
    val stats: SourceStats? get() = null

    /**
     * How large the *result* is, which is not the same question as [sourceSize].
     *
     * A crop changes the frame: the picture drawn after it has the crop's dimensions
     * while operations are still addressed against the original. The shell measures
     * its canvas with this, so a cropped photograph is letterboxed in a box of its own
     * proportions rather than the source's.
     *
     * Defaulted, because for a medium whose edits never change the frame the two are
     * the same — and because a default is what keeps this from being a breaking change
     * to every engine that does not care.
     *
     * Takes a document and returns an `IntSize`: no raster type appears, so this stays
     * a question the video engine can answer (§3.3).
     */
    fun drawnSize(document: EditDocument): IntSize = sourceSize

    /**
     * The edit this file already carries, or an empty document (ME-134).
     *
     * The shell asks the engine rather than reading it itself: a recipe is a thing
     * only the medium's implementation knows how to find, and `EditRecipe` lives in
     * `jvmMain` where the shell cannot name it (§3.3). Defaulted, so a medium with
     * no notion of a stored edit is unaffected.
     */
    fun storedDocument(): EditDocument = EditDocument()

    /**
     * What **another** frame already carries, for the session that walks between them
     * (ME-169). Only the engine can answer: the shell is not allowed to know a file has
     * frames in it (§3.3). Defaulted to [storedDocument], which is a medium with one
     * picture — what the video engine inherits without a line.
     */
    fun storedDocumentIn(frameIndex: Int): EditDocument = storedDocument()

    /** The file-wide layer alone, which a frame's own work sits on top of (ME-175). */
    fun storedBase(): EditDocument = storedDocument()

    /**
     * How a **source** coordinate lands on the picture this engine draws for [document].
     *
     * Identity for a medium whose preview is its source laid out plainly, which is why
     * it is defaulted. It stops being identity the moment an operation moves pixels
     * about: with a flip applied, source `(100, 80)` is drawn at `(540, 80)`, and an
     * overlay that placed a stored rectangle without asking would draw it on the
     * opposite side of the picture from the region it actually keeps — measured, and
     * exactly what "the crop rectangle flew off the picture" was (2026-08-22).
     *
     * A `Matrix` and an `EditDocument`, so no raster type is named and the video engine
     * can answer it too (§3.3).
     */
    fun previewMatrix(document: EditDocument): Matrix = Matrix()

    /**
     * What is inside this file, as far as editing is concerned (ME-156).
     *
     * Defaulted to one picture, which is both the overwhelmingly common still and
     * the truth for a video timeline as far as a frame scope is concerned — so the
     * video engine and every test fake compile untouched.
     */
    val frames: EditorFrames get() = EditorFrames()

    /**
     * Writes [document] into the file that was opened — the only save there is
     * (§5). Nothing is chosen: no target, no name, no confirmation.
     *
     * Runs off the UI thread and reports through [EditSaveResult] rather than
     * throwing: §4.5's banner needs an outcome, and the picture must survive every
     * failure path.
     *
     * [scope] says whether the file's every frame draws this or only the one being
     * edited (ME-126). **Defaulted to [EditScope.AllFrames]**, which is both today's
     * behaviour and the honest word for a medium with one picture in it: a video
     * timeline has no frame to key on, so its engine ignores the parameter and
     * compiles unchanged.
     */
    suspend fun save(layers: EditLayers): EditSaveResult

    /**
     * Puts the file back exactly as it was shot, removing everything Woka wrote into
     * it (ME-107).
     *
     * Asked of the engine for the same reason [storedDocument] is: what an edit is
     * stored *in* is the medium's own business, and the shell cannot name it (§3.3).
     * Answers [EditSaveResult.NothingToDo] for a file that carries nothing, so the
     * control can be honest without the shell reading the file itself.
     *
     * Defaulted, so a medium that has nothing to remove is unaffected.
     */
    suspend fun revert(
        scope: EditScope = EditScope.AllFrames,
        frameIndex: Int = 0
    ): EditSaveResult = EditSaveResult.NothingToDo
}
