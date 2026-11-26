package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.totalMs
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The tracks under the transport (`.timeline-scroll`): the ruler, the clip lane and — once the
 * film carries a Markup shape — the Markup lane, each beside its 44 dp label, with the play
 * head, the skim line and the snap guide drawn once across all of them (RD-301).
 *
 * **One geometry for every lane.** The ruler's labels, a Markup bar and the play head are placed
 * by the clip lane's `StripGeometry`, so a zoom, a scroll or an edge drag moves them together
 * and a bar snapped to a join is drawn on it. The lanes scroll vertically when the author's
 * height is less than they need; the overlay scrolls with them.
 */
@Composable
internal fun TimelineTracks(
    state: StripState,
    clips: List<TrimClip>,
    edits: EditorTimelineScope,
    document: EditDocument,
    view: TimelineView,
    cells: FilmstripCells?,
    peaks: AudioPeaks?,
    zoom: StripZoom,
    modifier: Modifier = Modifier
) {
    val bars = markupBarsIn(document, clips.totalMs())
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val lanePx = with(density) { (maxWidth - TimelineTokens.labelWidth - TimelineTokens.laneEnd).toPx() }.coerceAtLeast(0f)
        val gapPx = with(density) { JOIN_GAP.toPx() }
        val drag = state.drag
        // The trimmed source is laid out beside the film only while an outer end is dragged.
        val (lead, tail) = trimmedEnds(clips, state.durationMs, drag)
        val layout: StripLayout = { scale, scroll -> StripGeometry(clips, lanePx, gapPx, lead, tail, scale, scroll) }
        val carry = remember { EdgeCarry() }
        val geometry = carry.layout(drag as? StripDrag.Edge, layout, zoom)
        SideEffect {
            zoom.layoutNow = layout
            zoom.playHeadMsNow = { state.walk.timelineMs(clips, state.player.currentPositionMs) }
            carry.release(drag as? StripDrag.Edge, zoom, layout)
            view.geometry = geometry
        }
        FollowPlayHead(state.player, state.walk, clips, zoom, layout)
        val labelPx = with(density) { TimelineTokens.labelWidth.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .stripZoomWheel(zoom, layout, labelPx)
                .trackPointer(view, state, labelPx)
                .emptyPlaceMenu(view.menu)
        ) {
            Column {
                TrackLine(null, TimelineTokens.rulerHeight) { TimelineRuler(state, clips, geometry) }
                Spacer(Modifier.height(TimelineTokens.rulerGap))
                TrackLine(Icons.Static.Video, TimelineTokens.videoLaneHeight) {
                    ClipStrip(state, clips, edits, geometry, view, cells, peaks)
                }
                if (bars.isNotEmpty()) {
                    Spacer(Modifier.height(TimelineTokens.rowGap))
                    TrackLine(Icons.Static.PencilLine, markupLaneHeight(bars)) {
                        MarkupLane(bars, clips, edits, document, geometry, view, state)
                    }
                }
                if (zoom.zoomed) TrackLine(null, OVERVIEW_GRIP) { Box(Modifier.fillMaxSize()) { ZoomOverview(zoom, geometry) } }
                Spacer(Modifier.height(TimelineTokens.rowGap))
            }
            Box(Modifier.matchParentSize().padding(start = TimelineTokens.labelWidth, end = TimelineTokens.laneEnd)) {
                SkimLine(view, state)
                SnapGuide(view)
                PlayHead(state, clips, geometry)
            }
            TimelineMenu(view.menu, state, clips, edits, document)
        }
    }
}

/** One `.track-line`: the label column's glyph, then the lane to 16 dp from the right. */
@Composable
private fun TrackLine(icon: DrawableResource?, height: Dp, lane: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.width(TimelineTokens.labelWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(painterResource(icon), null, Modifier.size(TimelineTokens.labelGlyph), colorFilter = ColorFilter.tint(Theme.room.faint))
            }
        }
        Box(Modifier.weight(1f).fillMaxSize()) { lane() }
        Spacer(Modifier.width(TimelineTokens.laneEnd))
    }
}
