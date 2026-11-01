package com.woka.app.shell.nowplaying

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Now Playing's geometry, taken from `ai/mockups/design/now-playing.html` (accepted
 * 2026-11-01). Each value names the CSS rule it comes from, as `SidebarTokens` does.
 */
internal object NowPlayingTokens {
    /** `.np`: the block between the two hairlines — the same 8 dp above and below the row. */
    val BlockVertical = 8.dp
    val BlockHorizontal = 10.dp

    /** `.nprow`. */
    val RowHeight = 44.dp
    val RowPadding = 6.dp
    val RowGap = 9.dp

    /** `.cover`. */
    val CoverSize = 32.dp
    val CoverRadius = 6.dp

    /** `.rb`: a round button, and the glyphs inside it. */
    val ButtonSize = 26.dp
    val PlayGlyph = 13.dp
    val CloseGlyph = 10.dp

    /** `.nprow .t b` / `.t span`. */
    val TitleSize = 13.sp
    val SubtitleSize = 11.sp

    /** `.bub`: the anchor, its distance from the window's edges, and where the top corners start. */
    val BubbleSize = 48.dp
    val BubbleEdge = 16.dp
    val BubbleTop = 64.dp

    /** `.ring`: the box the progress arc is drawn in, the cover inside it, and the stroke. */
    val RingBox = 46.dp
    val BubbleCover = 40.dp
    val RingStroke = 2.dp

    /** `.bub .more .t`. */
    val CapsuleText = 150.dp

    /** `.more`'s width transition, the snap to a corner, the fade in and out. */
    const val CapsuleMs = 260
    const val SnapMs = 320
    const val FadeMs = 200
}
