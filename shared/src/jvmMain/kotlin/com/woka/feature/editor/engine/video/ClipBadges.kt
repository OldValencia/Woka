package com.woka.feature.editor.engine.video

import com.woka.core.data.PlaybackSpeed
import com.woka.feature.editor.tools.trim.TrimClip

/**
 * What a clip's name says (`.clip-name`, RD-301): how long it lasts in the result, and why it
 * is that long — `2.4 s`, `2.4 s · 2×`, `2.0 s · still`. A tile has to say why it is the width
 * it is (ME-216, ME-217); the corner badges and the freeze's orange wash of the floating band
 * became this one label, as in the prototype.
 */
internal fun clipName(clip: TrimClip): String {
    val length = "${clip.lengthMs / 1000L}.${clip.lengthMs % 1000L / 100L} s"
    return when {
        clip.isFreeze -> "$length · still"
        clip.speed != 1f -> "$length · ${speedLabel(clip.speed)}"
        else -> length
    }
}

/**
 * `¼×`, `½×`, `1.25×`, `2×` — the vulgar fractions read faster than `0.25×` on a narrow tile,
 * and everything else is the viewer's own `PlaybackSpeed.label`: two decimals, zeroes trimmed.
 */
internal fun speedLabel(speed: Float): String = when (speed) {
    0.25f -> "¼×"
    0.5f -> "½×"
    else -> PlaybackSpeed.label(speed)
}
