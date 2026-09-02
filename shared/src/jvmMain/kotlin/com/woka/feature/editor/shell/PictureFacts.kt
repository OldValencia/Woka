package com.woka.feature.editor.shell

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.SourceStats

/**
 * What the shell knows about the picture and a tool does not (ME-166).
 *
 * The three travel together everywhere — `ParamRows` receives them and hands the same
 * three to `ParamWriter` — and they answer one question between them: *what is open right
 * now*. Carried as one value rather than three parameters because the third one pushed
 * both files past the size mandate, and because a fourth fact would otherwise widen two
 * signatures again.
 *
 * **None of it reaches `stage()`.** A tool may consult these at the moment of a click,
 * through `EditorTool.resolve`, and what lands in the document is the resolved value —
 * which is what keeps the fold a pure function of parameters and lets one recipe serve
 * the preview and the save alike (§3.4).
 */
@Immutable
internal data class PictureFacts(
    /**
     * The frame the picture currently has — the **folded** result, so it moves when a
     * crop lands. Turning "long edge 2560" into a scale is what this is for (ME-110).
     * Zero before the canvas has measured, which resolves to nothing.
     */
    val frame: IntSize = IntSize.Zero,
    /**
     * The **source's** own dimensions, which is a different question from [frame]. A tool
     * may derive a row's default from this (ME-159); one derived from the folded size
     * would make a caption resize itself the moment a crop was applied.
     */
    val sourceSize: IntSize = IntSize.Zero,
    /**
     * A histogram and a black point, for a tool that corrects rather than sets. Null for
     * a medium with no still to measure, and until the proxy has been decoded — which is
     * also the whole of the window in which there is no picture on screen to click about.
     */
    val stats: SourceStats? = null
)
