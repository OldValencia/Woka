package com.woka.feature.editor.engine.video

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The docked timeline's measurements (RD-301), from the prototype's `editor.css` — `.timeline`
 * and the room overrides after it. Every colour is `Theme.room`'s; none is declared here.
 *
 * Its own object beside the timeline rather than inside `EditorTokens`: that file is the
 * shell's and at the size mandate, and nothing outside this package draws a lane.
 */
internal object TimelineTokens {

    /** `--timeline-height` is clamped to 200 … the window less 380 (`timeline.js` `setHeight`). */
    val minHeight = 200.dp
    val windowReserve = 380.dp

    /** `.timeline-resizer`: an 8 px band at the top edge, its grip 34 × 3, 56 wide on hover. */
    val resizerHeight = 8.dp
    val gripWidth = 34.dp
    val gripHoverWidth = 56.dp
    val gripHeight = 3.dp

    /** `.timeline .transport { padding: 10px 14px 6px }`. */
    val transportStart = 14.dp
    val transportTop = 10.dp
    val transportBottom = 6.dp
    val timeGap = 6.dp
    val buttonHeight = 32.dp
    val buttonRadius = RoundedCornerShape(8.dp)
    val buttonGlyph = 16.dp
    val zoomButton = 30.dp
    val dividerHeight = 18.dp

    /** `.timeline .track-line`: a 44 px label column, lanes to 16 px from the right, 6 px apart. */
    val labelWidth = 44.dp
    val labelGlyph = 16.dp
    val laneEnd = 16.dp
    val rowGap = 6.dp
    val rulerHeight = 27.dp
    val rulerGap = 2.dp
    val tickHeight = 4.dp

    /** The least room between two ruler labels; the step is the smallest 1/2/5 × 10ⁿ that leaves it. */
    val rulerLabelSpacing = 80.dp

    val laneShape = RoundedCornerShape(8.dp)
    val videoLaneHeight = 64.dp
    val waveHeight = 18.dp
    val markupBarHeight = 30.dp
    val markupRowGap = 4.dp

    /** `.clip-handle`: 10 wide, a 2 × 16 grip. */
    val handleWidth = 10.dp
    val gripLineWidth = 2.dp
    val gripLineHeight = 16.dp

    /** `.playhead`: 2 px, a 10 px dot 5 from the top; `.playhead-split` 26 × 22 at top 2. */
    val playheadWidth = 2.dp
    val playheadDot = 10.dp
    val playheadDotTop = 5.dp
    val scissorsWidth = 26.dp
    val scissorsHeight = 22.dp
    val scissorsTop = 2.dp
    val scissorsGlyph = 15.dp

    /** `.skim-badge` with its still (RD-301), 12 above the canvas's foot. */
    val skimStillWidth = 96.dp
    val skimStillHeight = 54.dp
    val skimLift = 12.dp

    /** `.context-menu { min-width: 216px }`. */
    val menuWidth = 216.dp

    /** A press this close to a bar's end takes the edge; snapping reaches this far (`snapTime`). */
    val edgeReach = 8.dp
    val snapReach = 8.dp

    /** `.clip-name`, the ruler and the readouts: the prototype's `--mono`. */
    val clipName = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    val ruler = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    val readout = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    val badge = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}
