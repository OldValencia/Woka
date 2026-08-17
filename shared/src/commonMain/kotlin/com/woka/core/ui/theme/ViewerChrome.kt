package com.woka.core.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chrome drawn on top of media: HUD badges, toolbar popovers, tooltips.
 *
 * Deliberately theme-independent and outside [Colors] — this sits on the frame,
 * not on an app surface, so it stays dark in both themes. What it replaces is 11
 * scattered `Color.Black.copy(alpha = …)` literals whose values did not drift
 * randomly but grouped into the three roles below; a single scrim token would have
 * flattened a real distinction.
 *
 * The two text styles are the Material defaults the viewer used to read through
 * `MaterialTheme.typography`, written out so the same pixels come from [Theme]
 * instead of from Material.
 */
object ViewerChrome {

    /** Circular HUD badges and the fullscreen control pill. */
    val hudScrim: Color = Color.Black.copy(alpha = 0.5f)

    /** Toolbar popovers and the zoom HUD card. */
    val popoverScrim: Color = Color.Black.copy(alpha = 0.6f)

    /** Tooltips — denser, because they are small and must stay readable over anything. */
    val tooltipScrim: Color = Color.Black.copy(alpha = 0.7f)

    /**
     * The **foreground** half of the on-media pair, and light in both themes for
     * the same reason the scrims above are dark in both.
     *
     * Added 2026-08-18, because the pair had only one half. Chrome over media took
     * its background from a scrim here and its text from `Theme.colors.textPrimary`,
     * which in the light theme is `#1C1C1E` — measured against [hudScrim], that
     * lands between **1.05 : 1** over a dark photograph and 6.68 : 1 over a bright
     * one, i.e. legibility decided by the picture. Anything drawn on a scrim reads
     * these instead.
     *
     * `feature/viewer/` still reads `Theme.colors` at ~27 call sites and is
     * migrated by its own ticket; the editor reads these from its first commit.
     */
    val onScrim: Color = Color(0xFFF4F4F6)

    /** Secondary text on a scrim — 4.34 : 1 over [hudScrim] on a mid photograph. */
    val onScrimSecondary: Color = Color(0xFFCDCDD2)

    /** Deliberately dim: 1.87 : 1 is what "disabled" is supposed to look like. */
    val onScrimDisabled: Color = Color(0xFF838893)

    /** Hairlines and dividers inside a scrim card. */
    val onScrimBorder: Color = Color.White.copy(alpha = 0.08f)

    /**
     * Hover and selection fills for controls on a scrim. Denser than the dock's
     * 2 %/3 %, which sits on an opaque island rather than on 50 % black over a
     * photograph — at 2 % on a scrim neither state is visible at all.
     */
    val onScrimHover: Color = Color.White.copy(alpha = 0.08f)
    val onScrimSelected: Color = Color.White.copy(alpha = 0.14f)

    val cornerRadius = 12.dp
    val tooltipCornerRadius = 6.dp

    /** Fixed, so popover content can grow without the cursor dropping out of the card. */
    val popoverWidth = 210.dp

    val popoverPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
    val hudPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    val tooltipPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)

    /** Was `MaterialTheme.typography.titleSmall`. */
    val hudLabel = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        fontWeight = FontWeight.Medium
    )

    /** Was `MaterialTheme.typography.labelSmall`. */
    val overlayLabel = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Medium
    )
}
