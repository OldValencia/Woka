package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.woka.feature.editor.tools.trim.TrimClip

const val CLIP_WAVE_TAG = "editor-clip-wave"

private val WAVE = Color.White.copy(alpha = 0.72f)

/**
 * [clip]'s sound as a min/max column per pixel, mirrored about the middle (ME-241) — one segment
 * of the sound row. Drawn from the stretch of source under each column, so a trim, a speed and a
 * zoom move it as they move the stills above. A freeze holds silence and draws none; the part not
 * yet measured is simply empty.
 */
@Composable
internal fun ClipWave(clip: TrimClip, peaks: AudioPeaks, fullWidthPx: Float, shiftPx: Float, modifier: Modifier) {
    if (clip.isFreeze) return
    val filled = peaks.filledMs.longValue
    Canvas(modifier.testTag(CLIP_WAVE_TAG)) {
        if (fullWidthPx <= 0f || size.width <= 0f) return@Canvas
        val middle = size.height / 2f
        val perPx = clip.sourceLengthMs / fullWidthPx
        var x = 0f
        while (x < size.width) {
            val fromMs = clip.fromMs + ((shiftPx + x) * perPx).toLong()
            val toMs = (fromMs + perPx.toLong().coerceAtLeast(1L)).coerceAtMost(clip.toMs)
            if (fromMs < filled) {
                val half = peaks.peakIn(fromMs, toMs) * size.height / 2f
                if (half > 0.5f) drawLine(WAVE, Offset(x, middle - half), Offset(x, middle + half), strokeWidth = 1f)
            }
            x += 1f
        }
    }
}
