package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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

/** The whole band, so it lines up with the rail rather than with the picture. */
private val BAND_WIDTH = 640.dp

private val BAND_PADDING = 10.dp
private val BAND_GAP = 10.dp

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
    val cells = rememberFilmstrip(holder.state?.file, duration, codedSize)
    if (player != null && duration > 0L) WalkClips(player, walk, clips)

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
        if (player != null && duration > 0L) {
            ClipStrip(player, walk, clips, cells, edits, duration, Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Readout(
            position = { player?.let { walk.timelineMs(clips, it.currentPositionMs) } ?: 0L },
            duration = clips.totalMs()
        )
    }
}

/**
 * Keeps the player inside the clip list: every reported position goes through
 * `ClipWalk.onPosition`, every edit of the list re-checks where the play head is, and a
 * press of play at the end of the result starts it again from the first clip.
 */
@Composable
private fun WalkClips(player: VideoPlayerState, walk: ClipWalk, clips: List<TrimClip>) {
    val current by rememberUpdatedState(clips)
    LaunchedEffect(player, walk) {
        snapshotFlow { player.currentPositionMs }.collect { walk.onPosition(player, current, it) }
    }
    LaunchedEffect(player, walk, clips) {
        walk.onPosition(player, clips, player.currentPositionMs)
    }
    LaunchedEffect(player, walk) {
        snapshotFlow { player.isPlaying }.collect { playing -> if (playing) walk.onPlay(player, current) }
    }
}
