package com.woka.feature.editor.tools.adjust

import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.SourceStats
import kotlin.math.log2
import kotlin.math.min

/**
 * 18 % grey carried through sRGB's transfer — what a correctly exposed photograph
 * averages to, and therefore what Auto aims the mean at.
 */
private const val TARGET_MEAN = 0.46f

/** What `blackPointMatrix` reaches at ±100, so an amount is a fraction of this. */
private const val BLACK_AT_FULL = 0.45f

/**
 * How much of the measured foot to claim. Not all of it: the 0.1 percentile still has
 * real detail below it, and setting black exactly there crushes what little is left.
 */
private const val BLACK_CLAIM = 0.8f

/** Below this the picture already has blacks, and the honest correction is none. */
private const val BLACK_FLOOR = 0.02f

/** The levels that count as crushed and as clipped — the last stop at either end. */
private const val SHADOW_LEVELS = 16
private const val HIGHLIGHT_LEVEL = 240

/** A quarter of the picture in the last stop earns about half the slider, and no more. */
private const val CLIP_GAIN = 200f
private const val CLIP_LIMIT = 60f

/** Half strength, so Auto is a correction the user can see past rather than a verdict. */
private const val EXPOSURE_STRENGTH = 0.5f

/**
 * Auto: five of the twelve sliders, computed from the picture (ME-167).
 *
 * **What it touches, and what it deliberately does not.** Black Point, Exposure, Shadows
 * and Highlights — the four a luma histogram can actually answer for. Contrast is left
 * alone because the level stretch has already added some, and adding it twice is what
 * makes an auto button look cheap. Saturation, Vibrance, Warmth and Tint are left alone
 * because colour balance from a *luma* histogram is a guess. Noise and Sharpness are left
 * alone because cleaning up grain nobody complained about is an edit the user did not
 * make.
 *
 * **It is idempotent, and that is a property to keep.** Every value is computed from
 * [stats] — the unedited source, measured once when the proxy arrived — and written
 * absolutely, never relative to what the sliders already say. Pressing Auto twice
 * therefore gives what pressing it once gives. A later edit that reads the current
 * params here would quietly break that, and nothing else would notice.
 *
 * **It replaces rather than blends**, which follows from `ActionParam`: `ParamWriter`
 * seeds an action from `tool.defaults()`, so Auto starts from neutral and manual work
 * done before it is discarded. That is the right reading of "Auto" — a correction of the
 * photograph, not of whatever was tried on it — and one `Undo` takes the whole thing back
 * because Adjust is `Single`.
 */
fun autoAdjustments(stats: SourceStats, base: ParamSet): ParamSet {
    val counted = stats.luma.sum()
    if (counted <= 0) return base

    val crushed = stats.luma.take(SHADOW_LEVELS).sum().toFloat() / counted
    val clipped = stats.luma.drop(HIGHLIGHT_LEVEL).sum().toFloat() / counted

    return base
        .with(AdjustTool.BlackPoint, blackAmount(stats.blackPoint))
        .with(AdjustTool.Exposure, exposureAmount(stats))
        .with(AdjustTool.Shadows, (crushed * CLIP_GAIN).coerceAtMost(CLIP_LIMIT))
        .with(AdjustTool.Highlights, -(clipped * CLIP_GAIN).coerceAtMost(CLIP_LIMIT))
}

/** Where the foot of the histogram sits, expressed as the slider that would put it there. */
private fun blackAmount(blackPoint: Float): Float {
    if (blackPoint <= BLACK_FLOOR) return 0f
    return (blackPoint * BLACK_CLAIM / BLACK_AT_FULL * 100f).coerceIn(0f, 100f)
}

/**
 * The multiply that moves the mean to [TARGET_MEAN], halved — and **capped by the
 * headroom the whites actually have**.
 *
 * `exposureMatrix` is `2^(amount/100)`, so the amount that scales a mean of `m` to `t` is
 * `100·log2(t/m)`. Without the cap a dark picture with one bright window would be lifted
 * until the window was a white hole, which is the failure mode every naive auto-exposure
 * has: the cap says the 99.9 percentile may reach white and not pass it.
 */
private fun exposureAmount(stats: SourceStats): Float {
    val mean = stats.meanLuma
    if (mean <= 0f) return 0f

    val wanted = 100f * log2(TARGET_MEAN / mean) * EXPOSURE_STRENGTH
    if (wanted <= 0f) return wanted.coerceAtLeast(-100f)

    val headroom = 100f * log2(1f / stats.whitePoint.coerceAtLeast(0.01f))
    return min(wanted, headroom).coerceIn(0f, 100f)
}
