package com.woka.feature.editor.engine.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.shell.EditorTokens
import com.woka.feature.editor.tools.trim.frameAt
import com.woka.feature.editor.tools.trim.frameCount
import com.woka.feature.editor.tools.trim.secondAndFrame
import com.woka.feature.viewer.shell.ViewerTooltip
import com.woka.feature.viewer.video.formatTime
import com.woka.feature.viewer.video.usesHours

/** `DockItem`'s own target, which is what the transport reads as. */
private val TRANSPORT = 26.dp

/** Wide enough for `42:33:12 / 47:10:05` without the row re-flowing as it counts. */
private val READOUT_WIDTH = 104.dp

/** And for `1:02:33:12 / 1:47:10:05`, once the result is an hour long. */
private val READOUT_WIDTH_HOURS = 124.dp

/** Narrower than [TRANSPORT]: the frame steps flank it and are not its equals. */
private val STEP_WIDTH = 18.dp

/**
 * The two small pieces [VideoEditorBand] is made of besides the strip, apart because that
 * file reached the size mandate. The seam is the usual one: the band is a *layout*, and
 * these are what it lays out. The third piece, the slider's style, left with the slider
 * when the strip replaced it (ME-210).
 */

/**
 * **A `Box` that centres, not a sized `BasicText`** (2026-09-03). Given only a `size`, the
 * text lays itself out at the box's top-left and is clipped by it — on screen the glyph
 * sat against the left edge of the band, half of it gone. Every other round control in
 * the app is a `Box` with `Alignment.Center` and its glyph inside; this is that.
 */
@Composable
internal fun Transport(playing: Boolean, onToggle: () -> Unit) {
    ViewerTooltip(if (playing) "Pause   ·   Space" else "Play   ·   Space") {
        Box(
            modifier = Modifier
                .size(TRANSPORT)
                .clip(RoundedCornerShape(EditorTokens.Rail.itemRadius))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onToggle)
                .testTag("editor-band-transport"),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = if (playing) "❙❙" else "▶",
                style = TextStyle(color = Theme.viewer.onScrim, fontSize = 12.sp)
            )
        }
    }
}

/**
 * One frame back or forward, either side of [Transport] (ME-219). The keys do the same and
 * Alt makes them a second; the tooltip says so, because nothing else on screen does.
 */
@Composable
internal fun FrameStep(direction: Int, onStep: () -> Unit) {
    val back = direction < 0
    ViewerTooltip(if (back) "Previous frame  ←   ·   Alt+←  1 s back" else "Next frame  →   ·   Alt+→  1 s on") {
        Box(
            modifier = Modifier
                .size(STEP_WIDTH, TRANSPORT)
                .clip(RoundedCornerShape(EditorTokens.Rail.itemRadius))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onStep)
                .testTag(if (back) "editor-band-frame-back" else "editor-band-frame-on"),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = if (back) "❙◀" else "▶❙",
                style = TextStyle(color = Theme.viewer.onScrimSecondary, fontSize = 9.sp)
            )
        }
    }
}

/**
 * [position] stays a lambda for the reason the slider's value does: this text is the one
 * thing in the row that changes every frame, and reading it here keeps the recomposition
 * to itself.
 *
 * **`m:ss:ff`, the frame inside its second** (ME-219), on the result's frame grid — so a
 * step moves the last field by one. The shape is the result's length's, not each value's,
 * so the row does not re-flow as the play head crosses an hour. Without a player, plain time.
 */
@Composable
internal fun Readout(position: () -> Long, duration: Long, fps: Double?) {
    val hours = usesHours(duration)
    val last = fps?.let { (frameCount(duration, it) - 1L).coerceAtLeast(0L) }
    BasicText(
        text = "${timecode(position(), fps, hours, last)} / ${timecode(duration, fps, hours, null)}",
        modifier = Modifier.width(if (hours) READOUT_WIDTH_HOURS else READOUT_WIDTH).testTag("editor-band-readout"),
        style = TextStyle(
            color = Theme.viewer.onScrimSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
}

/**
 * [ms] as `m:ss:ff`. A play head is clamped to the [last] frame: the end of the result is
 * where its last frame stops, not a frame of its own. The length is the frame count.
 */
internal fun timecode(ms: Long, fps: Double?, withHours: Boolean, last: Long?): String {
    if (fps == null) return formatTime(ms, withHours)
    val frame = if (last == null) frameCount(ms, fps) else frameAt(ms, fps).coerceAtMost(last)
    val (second, inSecond) = secondAndFrame(frame, fps)
    return "${formatTime(second * 1000L, withHours)}:${inSecond.toString().padStart(2, '0')}"
}
