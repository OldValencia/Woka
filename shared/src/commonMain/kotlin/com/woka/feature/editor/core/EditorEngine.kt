package com.woka.feature.editor.core

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
 * mandate. What the **source** is, is [EditorEnginePicture], extended for the same
 * reason at ME-302. What is left here is the file's identity and everything that
 * reads or writes what it carries.
 */
interface EditorEngine : EditorEngineSurface, EditorEnginePicture {

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
