package com.woka.feature.editor.core

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.StillColorInfo

/**
 * The half of the engine seam that says **what the picture is** — split out of
 * [EditorEngine] when ME-302 needed one more property than that file had room for.
 *
 * The cut follows the one [EditorEngineSurface] made at ME-156, and along the same
 * kind of line: those three *draw*, and every one of these *answers a question about
 * the source* — how large it is, what colour it was written in, what its histogram
 * says, where a source pixel lands and how many pictures are inside it. None of them
 * is `@Composable` and every one can be called from a coroutine or a test.
 *
 * **It still names no bitmap, player or recipe** (§3.3): sizes, matrices, numbers and
 * a colour description, all of which the video engine can answer too. That constraint
 * is the whole reason the seam exists, and splitting it must not relax it.
 *
 * [EditorEngine] extends this, so no implementation and no call site changes — an
 * override of a grandparent's property is still an override.
 */
interface EditorEnginePicture {

    /** What operations address, in source pixels — never the canvas or the proxy. */
    val sourceSize: IntSize

    /**
     * What colour the source was written in, asked of the **file** (ME-302).
     *
     * The third of three questions about whether a tool belongs on screen, and the
     * only one nothing else could answer: `ToolManifest.supports` asks it of the
     * *medium* and Settings asks it of the *preference*, while this asks it of the one
     * file that is open. `EditorTool.appliesTo` is what reads it.
     *
     * **SDR by default**, so `VideoEditorEngine` and every test fake inherit it without
     * a line and a video keeps the answer ME-180 gave its HDR switch — the tone map
     * reaches nothing on a moving frame, so nothing about it is worth showing. ME-301 is
     * the ticket that changes that, and it changes it here.
     *
     * Deliberately **not** part of [stats]: those are measured off the proxy and are
     * null until the canvas has decoded one, so anything filtered on them would appear
     * and then be taken away.
     */
    val sourceColor: StillColorInfo get() = StillColorInfo.Sdr

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
}
