package com.woka.feature.editor.engine.video

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.frameAt
import com.woka.feature.editor.tools.trim.frameCount
import com.woka.feature.editor.tools.trim.secondAndFrame
import com.woka.feature.viewer.video.formatTime
import com.woka.feature.viewer.video.usesHours

/** Wide enough for `42:33:12 / 47:10:05` in the mono face without the row re-flowing as it counts. */
private val READOUT_WIDTH = 150.dp

/** And for `1:02:33:12 / 1:47:10:05`, once the result is an hour long. */
private val READOUT_WIDTH_HOURS = 180.dp

/**
 * The play head's place over the result's length (`.timeline .time`): the position in the
 * room's text, ` / total` in its faint tone, mono so the digits do not shift as they count.
 *
 * [position] stays a lambda: this text is the one thing in the row that changes every frame,
 * and reading it here keeps the recomposition to itself.
 *
 * **`m:ss:ff`, the frame inside its second** (ME-219, kept by the author for RD-301 over the
 * prototype's tenths), on the result's frame grid — so a step moves the last field by one.
 * Without a player, plain time.
 *
 * **The shape is the source's length's** — [sourceMs] — not each value's (measured 2026-11-05):
 * a trim taking the result under an hour narrowed the readout mid-drag. **Or the result's, when
 * that is longer** — slowed or frozen past an hour (1.0.6 review).
 */
@Composable
internal fun Readout(position: () -> Long, duration: Long, fps: Double?, sourceMs: Long = duration) {
    val hours = usesHours(maxOf(sourceMs, duration))
    val last = fps?.let { (frameCount(duration, it) - 1L).coerceAtLeast(0L) }
    val text = buildAnnotatedString {
        append(timecode(position(), fps, hours, last))
        withStyle(SpanStyle(color = Theme.room.faint)) { append("  /  ${timecode(duration, fps, hours, null)}") }
    }
    BasicText(
        text = text,
        modifier = Modifier.width(if (hours) READOUT_WIDTH_HOURS else READOUT_WIDTH).testTag("editor-timeline-readout"),
        style = TimelineTokens.readout.copy(color = Theme.room.text)
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
