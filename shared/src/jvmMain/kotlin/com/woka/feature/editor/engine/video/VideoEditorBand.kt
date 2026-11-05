package com.woka.feature.editor.engine.video

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.shell.EditorTokens
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.editor.tools.trim.wholeSource
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.playRanges
import com.woka.feature.viewer.video.engine.releaseRanges

/** The whole band, so it lines up with the rail rather than with the picture. */
private val BAND_WIDTH = 640.dp

private val BAND_PADDING = 10.dp
private val BAND_GAP = 10.dp

/** The clip bar's height and its lift above the strip (`ClipBar`). */
private val CLIP_BAR_ROOM = 40.dp

internal const val EDITOR_BAND_TAG = "editor-band"

/**
 * The band under the canvas: a transport, the strip of clips and a readout (ME-206, then
 * ME-210…ME-214 for the strip).
 *
 * **The container is the rail's own** — `Theme.viewer.popoverScrim`, the rail's radius and
 * its padding — because it is chrome of the same kind sitting at the same edge, one gap
 * above it. The numbers mirror `ai/mockups/design/video-editor.html`, states 1–4.
 *
 * **Everything it shows is in timeline time.** The readout is the play head's place in the
 * *result* over the result's length, and the strip lays out what the document keeps — so
 * after a cut the band tells the truth about what `Done` and a render will contain. An
 * unedited video is one clip covering the file, which is the slider this replaced.
 *
 * **It draws before there is a player.** The holder is filled by the canvas's own
 * `DisposableEffect`, which may run after this composes, so an empty strip and `0:00` are
 * a state this has to have rather than a case to guard against.
 */
@Composable
internal fun VideoEditorBand(
    holder: VideoPlayerHolder,
    document: EditDocument,
    edits: EditorTimelineScope,
    modifier: Modifier = Modifier,
    /** The coded frame size the filmstrip decodes against; zero draws an empty strip. */
    codedSize: IntSize = IntSize.Zero
) {
    val player = holder.state
    val duration = player?.durationMs ?: 0L
    val clips = TrimTool.clipsIn(document) ?: wholeSource(duration)
    val walk = holder.walk
    val zoom = remember(holder) { StripZoom() }
    val cells = rememberFilmstrip(holder.state?.file, duration, codedSize)
    if (player != null && duration > 0L) WalkClips(player, walk, clips)
    if (player != null && duration > 0L) MixClipSound(player, walk, clips, document, edits)
    // The selected clip's bar floats above the band; the room is kept so the inspector over
    // the band moves up rather than under it (reported 2026-11-05).
    val barShown = clips.any { it.id == edits.selectedId } && (edits.editing || (!edits.perClip && clips.size > 1))
    val room by animateDpAsState(if (barShown) CLIP_BAR_ROOM else 0.dp)

    Row(
        modifier = modifier
            .padding(top = room)
            .width(BAND_WIDTH)
            .clip(RoundedCornerShape(EditorTokens.Rail.containerRadius))
            .background(Theme.viewer.popoverScrim)
            .padding(horizontal = BAND_PADDING, vertical = EditorTokens.Rail.containerPadding)
            .bandZoomWheel(zoom)
            .testTag(EDITOR_BAND_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BAND_GAP)
    ) {
        FrameStep(direction = -1) { player?.let { walk.step(it, -1, bySecond = false) } }
        Transport(playing = player?.let { walk.isPlaying(it) } == true) {
            player?.let { walk.toggle(it) }
        }
        FrameStep(direction = 1) { player?.let { walk.step(it, 1, bySecond = false) } }
        if (player != null && duration > 0L) {
            ClipStrip(player, walk, clips, cells, edits, duration, zoom, Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        ZoomPill(zoom)
        Readout(
            position = { player?.let { walk.timelineMs(clips, it.currentPositionMs) } ?: 0L },
            duration = clips.totalMs(),
            fps = player?.frameRate()
        )
    }
}

/**
 * Hands the clip list to the player as its kept ranges (ME-232), again on every edit of it.
 * The player joins them, holds a freeze and stops at the end on its own; the walk only
 * reads where it is. The list goes back when the band leaves.
 */
@Composable
private fun WalkClips(player: VideoPlayerState, walk: ClipWalk, clips: List<TrimClip>) {
    LaunchedEffect(player, walk, clips) {
        walk.clips = clips
        player.playRanges(clips.map { it.toSourceRange() }, walk)
    }
    DisposableEffect(player, walk) { onDispose { player.releaseRanges(walk) } }
}
