package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.shell.ScrubLevel
import com.woka.feature.editor.shell.ScrubRow
import com.woka.feature.editor.tools.trim.CLIP_SPEED_MAX
import com.woka.feature.editor.tools.trim.CLIP_SPEED_MIN
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.snapClipSpeed
import com.woka.feature.editor.tools.trim.withSpeed

/**
 * The clip's speed as a scrub row (RD-302): ¼× to 4× **in octaves**, so 1× sits at the centre
 * and ½× and 2× as far from it (the author, 2026-11-26) — the old slider's reason for being
 * logarithmic. It sticks to the old detents on the way past (`snapClipSpeed`).
 */
internal val CLIP_SPEED = FloatParam(
    key = "speed",
    label = "Speed",
    range = CLIP_SPEED_MIN..CLIP_SPEED_MAX,
    default = 1f,
    unit = "×",
    icon = Icons.Static.Speed,
    octaves = true
)

/**
 * **A drag shows its value and writes nothing until it lets go** — the old chip's rule (ME-216):
 * a preview per move would re-lay every clip after this one and re-map the play head under it.
 * A tap, a release or a typed number writes once: one step, and none when it is the same speed.
 */
@Composable
internal fun ClipSpeedRow(holder: VideoPlayerHolder, clip: TrimClip) {
    var dragging by remember(clip.id) { mutableStateOf<Float?>(null) }
    fun write(speed: Float) {
        dragging = null
        holder.strip?.let { strip -> strip.apply(strip.clips.withSpeed(clip.id, speed)) }
    }
    ScrubRow(
        ScrubLevel(
            spec = CLIP_SPEED,
            value = dragging ?: clip.speed,
            onWrite = { value, settled -> if (settled) write(snapClipSpeed(value)) else dragging = snapClipSpeed(value) },
            settle = { dragging?.let(::write) },
            reset = { write(1f) }
        )
    )
}
