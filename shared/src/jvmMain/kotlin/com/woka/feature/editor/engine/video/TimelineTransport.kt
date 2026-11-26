package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * The timeline's first row (`.timeline .transport`): play and the time on the left; Split and
 * the zoom on the right (RD-301).
 *
 * **No frame-step buttons** (the author, 2026-11-26): the readout stays frame-accurate and
 * `Alt+←`/`→` still step a frame; the ❙◀ ▶❙ that flanked the band's play button are gone, as
 * in the prototype. **The zoom factor sits between − and + while zoomed** (the author's
 * 2026-11-04 "show that a zoom has happened"); a click on it fits the film again.
 */
@Composable
internal fun TimelineTransport(state: StripState?, clips: List<TrimClip>, zoom: StripZoom) {
    val player = state?.player
    Row(
        Modifier.fillMaxWidth().padding(
            start = TimelineTokens.transportStart,
            top = TimelineTokens.transportTop,
            end = TimelineTokens.transportStart,
            bottom = TimelineTokens.transportBottom
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TimelineTokens.timeGap)) {
            PlayButton(playing = player?.let { state.walk.isPlaying(it) } == true) { player?.let { state.walk.toggle(it) } }
            Readout(
                position = { player?.let { state.walk.timelineMs(clips, it.currentPositionMs) } ?: 0L },
                duration = clips.totalMs(),
                fps = player?.frameRate(),
                sourceMs = player?.durationMs ?: 0L
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TransportButton("Split", "Split the clip at the playhead   ·   S", Icons.Static.Trim, "editor-timeline-split") {
                state?.splitAtPlayHead()
            }
            Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(TimelineTokens.dividerHeight).background(Theme.room.line))
            TransportButton("−", "Show more of the video   ·   Ctrl + wheel", null, "editor-timeline-zoom-out") { zoom.wheelAtPlayHead(1f) }
            if (zoom.zoomed) {
                TransportButton(zoomLabel(zoom.zoom), "Show the whole video", null, "editor-strip-zoom") { zoom.fit() }
            }
            TransportButton("+", "Show more detail   ·   Ctrl + wheel", null, "editor-timeline-zoom-in") { zoom.wheelAtPlayHead(-1f) }
        }
    }
}

/** `.transport-play`: one 32 dp round knob, the room's raised well. */
@Composable
private fun PlayButton(playing: Boolean, onToggle: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    ViewerTooltip(if (playing) "Pause   ·   Space" else "Play   ·   Space") {
        Box(
            Modifier
                .size(TimelineTokens.playSize)
                .clip(CircleShape)
                .background(if (hovered) Theme.room.knob else Theme.room.well2)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = hover, indication = null, onClick = onToggle)
                .testTag("editor-timeline-play"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(if (playing) Icons.Static.Pause else Icons.Static.PlayFill),
                contentDescription = if (playing) "Pause" else "Play",
                modifier = Modifier.size(TimelineTokens.playGlyph),
                colorFilter = ColorFilter.tint(Theme.room.text)
            )
        }
    }
}

/** `.transport .secondary` and `.icon-button.small`: muted, the text colour on the strong hover. */
@Composable
private fun TransportButton(label: String, tip: String, icon: DrawableResource?, tag: String, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val colour = if (hovered) Theme.room.text else Theme.room.muted
    ViewerTooltip(tip) {
        Row(
            Modifier
                .height(if (icon != null) TimelineTokens.buttonHeight else TimelineTokens.zoomButton)
                .clip(TimelineTokens.buttonRadius)
                .background(if (hovered) Theme.room.hoverStrong else Theme.room.panel)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = hover, indication = null, onClick = onClick)
                .padding(horizontal = if (icon != null) 10.dp else 9.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Image(painterResource(icon), null, Modifier.size(TimelineTokens.buttonGlyph), colorFilter = ColorFilter.tint(colour))
            }
            // − and + read at the prototype's 15 px; words and the zoom factor at 12.
            BasicText(label, style = TextStyle(color = colour, fontSize = if (label.length == 1) 15.sp else 12.sp))
        }
    }
}

/** `2×`, `8×`, `140×` — whole numbers past ten, one decimal under. */
private fun zoomLabel(zoom: Float): String =
    if (zoom >= 10f) "${zoom.roundToInt()}×" else "${(zoom * 10f).roundToInt() / 10f}×"
