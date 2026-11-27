package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.usesHours
import kotlin.math.roundToInt
import com.woka.feature.viewer.video.engine.frameRate

const val TIMELINE_SKIM_BADGE_TAG = "editor-timeline-skim-badge"

/**
 * `.skim-badge` with its still (RD-301): centred over the canvas column, 12 dp above the
 * timeline's top edge — the canvas's foot. A `Popup`, because the badge stands outside the
 * timeline's bounds.
 */
@Composable
internal fun SkimBadge(view: TimelineView, player: VideoPlayerState, clips: List<TrimClip>, cells: FilmstripCells?) {
    val ms = view.skimMs ?: return
    if (player.isPlaying) return
    val lift = with(LocalDensity.current) { TimelineTokens.skimLift.roundToPx() }
    val sourceMs = clips.atTimeline(ms)?.sourceMs
    val well = Theme.room.well
    Popup(popupPositionProvider = remember(lift) { AboveCentre(lift) }) {
        Column(
            Modifier
                .background(Theme.room.tip, RoundedCornerShape(7.dp))
                .padding(start = 5.dp, top = 5.dp, end = 5.dp, bottom = 3.dp)
                .testTag(TIMELINE_SKIM_BADGE_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Canvas(Modifier.size(TimelineTokens.skimStillWidth, TimelineTokens.skimStillHeight).clip(RoundedCornerShape(5.dp))) {
                drawRect(well)
                val still = sourceMs?.let { cells?.stillAt(it) } ?: return@Canvas
                val (offset, size) = centredCrop(still.width, still.height, size.width / size.height)
                drawImage(still, offset, size, dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()))
            }
            BasicText(
                timecode(ms, player.frameRate(), usesHours(clips.totalMs()), Long.MAX_VALUE),
                style = TimelineTokens.badge.copy(color = Theme.room.tipText)
            )
        }
    }
}

/** Centred on the anchor — the timeline — and [lift] px above its top edge. */
private class AboveCentre(private val lift: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset(
        anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
        anchorBounds.top - popupContentSize.height - lift
    )
}
