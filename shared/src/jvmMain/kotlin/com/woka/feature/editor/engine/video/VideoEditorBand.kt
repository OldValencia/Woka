package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.shell.EditorTokens
import com.woka.feature.viewer.shell.slider.MediaSlider
import com.woka.feature.viewer.video.engine.pauseForSeek
import com.woka.feature.viewer.video.engine.resumeAfterSeek
import com.woka.feature.viewer.video.engine.seek

/** The whole band, so it lines up with the rail rather than with the picture. */
private val BAND_WIDTH = 640.dp

private val BAND_PADDING = 10.dp
private val BAND_GAP = 10.dp

internal const val EDITOR_BAND_TAG = "editor-band"

/**
 * The band under the canvas: a transport, a scrubber and a readout (ME-206).
 *
 * **The container is the rail's own** — `Theme.viewer.popoverScrim`, the rail's radius and
 * its padding — because it is chrome of the same kind sitting at the same edge, one gap
 * above it. Its geometry is private to this file rather than in `EditorTokens`: that
 * object's KDoc scopes it to the rail, the inspector and the editor's top bar, and what
 * the band *holds* belongs to the medium. The numbers mirror
 * `ai/mockups/design/video-editor.html`, state 1.
 *
 * **Nothing here is a new control.** `MediaSlider` is the app's own, and its `value` is a
 * `() -> Float` precisely so a caller tracking a play head at the presenter's frame rate
 * does not perform the state read in its own scope and drag its whole subtree into every
 * frame — so the lambda is passed down rather than hoisted. `formatTime` is the viewer's.
 * The seek is `pauseForSeek` → `seek` → `resumeAfterSeek`, the sequence
 * `MediaViewerTimeSlider` already uses, which is what makes a drag hold the picture and
 * release it back into play only if it was playing.
 *
 * **It draws before there is a player.** The holder is filled by the canvas's own
 * `DisposableEffect`, which may run after this composes, so an empty track and `0:00` are
 * a state this has to have rather than a case to guard against.
 */
@Composable
internal fun VideoEditorBand(holder: VideoPlayerHolder, modifier: Modifier) {
    val player = holder.state
    val duration = player?.durationMs ?: 0L
    var dragging by remember { mutableStateOf(false) }
    var dragged by remember { mutableStateOf(0f) }

    Row(
        modifier = modifier
            .width(BAND_WIDTH)
            .clip(RoundedCornerShape(EditorTokens.Rail.containerRadius))
            .background(Theme.viewer.popoverScrim)
            .padding(horizontal = BAND_PADDING, vertical = EditorTokens.Rail.containerPadding)
            .testTag(EDITOR_BAND_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BAND_GAP)
    ) {
        Transport(playing = player?.isPlaying == true) {
            player?.let { it.isPlaying = !it.isPlaying }
        }
        MediaSlider(
            value = {
                // The drag target while a pointer is down, so the knob follows the hand
                // rather than the decoder catching up to it.
                if (dragging) dragged else fractionOf(player?.currentPositionMs ?: 0L, duration)
            },
            onValueChange = { at ->
                if (!dragging) {
                    dragging = true
                    player?.pauseForSeek()
                }
                dragged = at
                player?.seek((at * duration).toLong())
            },
            onValueChangeFinished = {
                dragging = false
                player?.resumeAfterSeek()
            },
            style = bandSliderStyle(),
            modifier = Modifier.weight(1f)
        )
        Readout(position = { player?.currentPositionMs ?: 0L }, duration = duration)
    }
}
