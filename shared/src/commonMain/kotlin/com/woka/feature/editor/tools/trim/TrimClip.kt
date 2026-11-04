package com.woka.feature.editor.tools.trim

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * One clip of the timeline: a range of the **one** source file, under a stable id
 * (ME-210, `timeline.md` §3.1).
 *
 * **The id is minted once and never reused**, exactly as `MarkupShape.id` is: the strip's
 * selection is keyed on it and must survive a move, which replaces the instance — and a
 * per-clip edit (ME-215) will be keyed on it too, where a *position* would hand every clip
 * its neighbour's edit the moment the row was re-ordered.
 *
 * **Only what the strip's geometry needs** (§3.2): the range, the key, the speed (ME-216).
 * A freeze's hold joins the same way (ME-217); volume and the picture tools never will,
 * because they change how a clip looks rather than how long it lasts.
 */
@Immutable
@Serializable
data class TrimClip(
    val id: String,
    /** Where the clip starts, in source milliseconds. */
    val fromMs: Long,
    /** Where it ends, exclusive, in source milliseconds. */
    val toMs: Long,
    /**
     * Which per-clip layer this clip draws (ME-215): the `EditLayers` / `EditPayload.frames`
     * key, an `Int` because that machinery is keyed on one. 0 is the whole-file clip's, so a
     * film cut before ME-215 keeps reading as it did. Split and duplicate mint a fresh key
     * and copy the layer onto it; a reorder touches nothing, so a look follows its clip.
     */
    val key: Int = 0,
    /**
     * How fast this clip plays, ¼× to 4× (ME-216). A field rather than an op in the clip's
     * layer because it changes the clip's **length**, and the strip has to be drawable from
     * the list alone (`timeline.md` §3.2).
     */
    val speed: Float = 1f
) {
    /** How much of the source the clip spans. */
    val sourceLengthMs: Long get() = (toMs - fromMs).coerceAtLeast(0L)

    /** How long it lasts on the timeline — the source span at its [speed]. */
    val lengthMs: Long get() = (sourceLengthMs / speed).toLong()
}

/** The quick speeds the clip bar offers as one click each, slowest first (ME-216). */
val CLIP_SPEEDS = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f)

/** The speeds the slider sticks to on its way past (2026-11-04: 1.25×, 1.5× wanted). */
val CLIP_SPEED_DETENTS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)

const val CLIP_SPEED_MIN = 0.25f
const val CLIP_SPEED_MAX = 4f

/**
 * [speed] on the slider's way to it: steps of 0.05, pulled onto a detent nearby. Measured in
 * octaves so the pull is the same at ½× as at 2× — the slider is logarithmic for that reason.
 */
fun snapClipSpeed(speed: Float): Float {
    val clamped = speed.coerceIn(CLIP_SPEED_MIN, CLIP_SPEED_MAX)
    CLIP_SPEED_DETENTS.firstOrNull { abs(log2(it) - log2(clamped)) < 0.06f }?.let { return it }
    return ((clamped * 20f).roundToInt() / 20f).coerceIn(CLIP_SPEED_MIN, CLIP_SPEED_MAX)
}

/**
 * Where a timeline position lands: which clip, and which source millisecond inside it.
 *
 * The index rather than the id, because the same source range may appear twice after a
 * duplicate — the source millisecond alone cannot say which copy is playing.
 */
@Immutable
data class ClipPosition(val index: Int, val sourceMs: Long)
