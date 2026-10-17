package com.woka.app.shell.sidebar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sidebar's geometry, taken from `ai/mockups/design/sidebar-redesign.html`. Each
 * value names the CSS rule it comes from, so a change to the mockup has one place to
 * land.
 */
internal object SidebarTokens {
    /** `.side` width. */
    val Width = 236.dp

    /** `.side-top`, and the toolbar beside it: the two share one row (SIDE-003). */
    val HeaderHeight = 52.dp

    /** `.side-scroll` horizontal padding: the inset of every row from the edges. */
    val ListInset = 10.dp

    /** `.row` height and radius. */
    val RowHeight = 30.dp
    val RowRadius = 7.dp

    /** The pinned row carries a second line, `.sub`, under its name. */
    val PinnedRowHeight = 40.dp

    /** `.row` inner padding, and the gap between its icon, label and count. */
    val RowPadding = 8.dp

    /** `.l1` / `.l2`: each tree level steps in by this much. */
    val Indent = 16.dp

    val IconSize = 16.dp
    val ChevronSize = 12.dp

    /** `.side-top` padding. */
    val HeaderStart = 18.dp
    val HeaderEnd = 12.dp

    /** `.logo`: a square running from the accent to `#5E5CE6`. */
    val LogoSize = 20.dp
    val LogoRadius = 6.dp
    val LogoGradientEnd = Color(0xFF5E5CE6)

    /** `.sec`: the gap above a section label. */
    val SectionTop = 14.dp

    val LabelSize = 13.sp
    val SubSize = 11.sp
    val SectionSize = 11.sp
}
