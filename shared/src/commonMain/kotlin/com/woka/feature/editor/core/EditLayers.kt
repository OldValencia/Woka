package com.woka.feature.editor.core

/**
 * What a sitting has to write, in the two layers ME-175 gave the file (2026-09-02).
 *
 * **The write used to be handed composed documents and could not tell the halves
 * apart.** A frame's document is the shared layer plus its own work, so writing it whole
 * into that frame's slot put the shared half there too — an adjustment made for every
 * picture ended up on the two frames that happened to be visited, and nowhere else. The
 * session knows which half is which; nothing below it can work it out.
 *
 * [frames] holds **only what each frame adds** on top of [base], and a frame that adds
 * nothing is absent rather than present and empty: an empty key still says "this frame
 * has an edit of its own", which is what the scope switch reads back.
 */
data class EditLayers(
    /** The file-wide edit — `EditPayload.ops`. */
    val base: EditDocument,
    /** What each frame adds to it, by frame index. Absent means "adds nothing". */
    val frames: Map<Int, EditDocument> = emptyMap(),
    /**
     * Every frame this sitting opened, whether or not it ended up adding anything.
     *
     * **"Not visited" and "visited and emptied" are different answers** and [frames]
     * cannot tell them apart, because both are absent from it. The first must leave the
     * file's key alone — a burst's other four pictures are not edited by having been in
     * the same file — and the second must remove it.
     */
    val touched: Set<Int> = emptySet()
)
