package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.wholeSource
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.playRanges
import com.woka.feature.viewer.video.engine.releaseRanges

internal const val EDITOR_TIMELINE_TAG = "editor-timeline"

/**
 * The film's timeline, docked under the canvas beside the inspector (RD-301, `#editor/video`):
 * the transport, then the tracks — ruler, the clip lane, the Markup lane — on the room's panel.
 *
 * **It replaced a 640 dp band floating over the picture's foot** (ME-206 … ME-241). That card
 * held a long film to a fixed width and covered the frame it was cutting; docked, the lanes
 * take the column's width and the height the author gives them.
 *
 * **Everything it shows is in timeline time**, as the band's was: the readout, the ruler and
 * the lanes lay out what the document keeps, so after a cut they show what `Done` and a render
 * will contain. An unedited film is one clip covering the file.
 *
 * **It draws before there is a player.** The holder is filled by the canvas's own
 * `DisposableEffect`, which may run after this composes, so a transport with nothing to play
 * is a state this has, not a case to guard against.
 */
@Composable
internal fun VideoEditorTimeline(
    holder: VideoPlayerHolder,
    document: EditDocument,
    edits: EditorTimelineScope,
    modifier: Modifier = Modifier,
    /** The coded frame size the filmstrip decodes against; zero draws empty stills. */
    codedSize: IntSize = IntSize.Zero
) {
    val player = holder.state
    val duration = player?.durationMs ?: 0L
    val clips = TrimTool.clipsIn(document) ?: wholeSource(duration)
    val zoom = remember(holder) { StripZoom() }
    val cells = rememberFilmstrip(player?.file, duration, codedSize)
    val peaks = rememberAudioPeaks(player?.file, duration)
    val height = rememberTimelineHeight()
    val view = remember(holder) { TimelineView() }

    Box(modifier.height(height.value).background(Theme.room.panel).testTag(EDITOR_TIMELINE_TAG)) {
        Column(Modifier.fillMaxSize()) {
            if (player != null && duration > 0L) {
                val state = rememberStrip(holder, player, clips, edits, duration)
                WalkClips(player, holder.walk, clips)
                MixClipSound(player, holder.walk, clips, document, edits)
                TimelineTransport(state, clips, zoom)
                TimelineTracks(state, clips, edits, document, view, cells, peaks, zoom, Modifier.weight(1f))
            } else {
                TimelineTransport(null, clips, zoom)
            }
        }
        // `border-top: 1px solid var(--e-line)` — the panel's edge against the canvas.
        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.room.line))
        TimelineResizer(height, Modifier.align(Alignment.TopCenter))
        if (player != null) SkimBadge(view, player, clips, cells)
    }
}

/**
 * The gestures' state, refreshed every composition, and lent to the holder so the editor's
 * keyboard reaches the same clips (`timelineKey`) while the timeline is on screen.
 */
@Composable
private fun rememberStrip(
    holder: VideoPlayerHolder,
    player: VideoPlayerState,
    clips: List<TrimClip>,
    edits: EditorTimelineScope,
    durationMs: Long
): StripState {
    val state = remember(player, holder.walk) { StripState(player, holder.walk) }
    // Before the first draw as well, so a composable reading `state.edits` finds it set.
    remember(state) { state.refresh(clips, edits, durationMs) }
    SideEffect { state.refresh(clips, edits, durationMs) }
    DisposableEffect(holder, state) {
        holder.strip = state
        onDispose { if (holder.strip === state) holder.strip = null }
    }
    return state
}

/**
 * Hands the clip list to the player as its kept ranges (ME-232), again on every edit of it.
 * The player joins them, holds a freeze and stops at the end on its own; the walk only
 * reads where it is. The list goes back when the timeline leaves.
 */
@Composable
private fun WalkClips(player: VideoPlayerState, walk: ClipWalk, clips: List<TrimClip>) {
    LaunchedEffect(player, walk, clips) {
        walk.clips = clips
        player.playRanges(clips.map { it.toSourceRange() }, walk)
    }
    DisposableEffect(player, walk) { onDispose { player.releaseRanges(walk) } }
}
