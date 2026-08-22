package com.woka.feature.editor.tools.resize

import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/** The smallest a picture may be reduced to, as a percentage of what it is now. */
internal const val MIN_RESIZE_PERCENT = 5f

/** Full size. A resize at this composes no stage at all. */
internal const val FULL_PERCENT = 100f

/**
 * The arithmetic behind the resize tool (ME-110), with no Compose and no document.
 *
 * **Percent of the current frame, never absolute pixels.** `stage()` receives parameters
 * and never a frame (§3.4), so a target of "2560 wide" cannot become a matrix — the
 * scale is `target / frame`, and the frame is a running value only the fold holds. A
 * proportion also survives a crop changing the frame underneath it, which a stored pair
 * of pixel dimensions would not.
 *
 * Absolute targets stay reachable in the inspector, which *does* know the frame: they
 * are resolved to a percentage before anything is written, and that is what this file
 * is for.
 */

/**
 * The percentage that brings [frame]'s **longer** side to [target] pixels.
 *
 * Landscape and portrait answer the same proportion for the same target, which is what
 * "long edge" means — a 4000×3000 and a 3000×4000 both reach 2560 at 64 %.
 *
 * **Never above [FULL_PERCENT]**: this tool makes pictures smaller. A target larger than
 * the source is not an enlargement, it is a request the source cannot honour, and
 * answering 100 % is the honest form of "already smaller than that".
 */
fun scaleForLongEdge(target: Int, frame: IntSize): Float {
    val longest = max(frame.width, frame.height)
    if (longest <= 0 || target <= 0) return FULL_PERCENT
    val percent = target.toFloat() / longest * 100f
    return percent.coerceIn(MIN_RESIZE_PERCENT, FULL_PERCENT)
}

/**
 * What [percent] of [frame] comes out as, in whole pixels — the footnote's numbers.
 *
 * At least one pixel per side: a frame rounded to zero is one the renderer would hand
 * to Skia as a zero-sized surface.
 */
fun resizedFrame(percent: Float, frame: IntSize): IntSize {
    val factor = percent / 100f
    return IntSize(
        width = max(1, (frame.width * factor).roundToInt()),
        height = max(1, (frame.height * factor).roundToInt())
    )
}

/** `4000 × 3000 → 2000 × 1500`, or null when nothing is being resized. */
fun resizeFootnote(percent: Float, frame: IntSize): String? {
    if (frame.width <= 0 || frame.height <= 0) return null
    if (percent >= FULL_PERCENT) return null
    val target = resizedFrame(percent, frame)
    return "${frame.width} × ${frame.height} → ${target.width} × ${target.height}"
}
