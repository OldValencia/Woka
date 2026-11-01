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
}
