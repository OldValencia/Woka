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
     * **Which surfaces take these, and which must not** (ME-121, checked against the
     * tree 2026-09-01). The measurement above is against [hudScrim], and that is the
     * whole of the rule: a foreground takes an `onScrim*` token when it is drawn on
     * one of the three scrims **in this object**, which are dark whatever the theme.
     * A foreground drawn on a plate derived from `Theme.colors` keeps `Theme.colors`,
     * because plate and text invert together — the viewer's control pill paints
     * `Theme.colors.surface` (`0xCC`-alpha, light or dark by theme), and its arrows and
     * video bar sit on a clipped wash of `Theme.colors.background`. Converting those
     * would put `#F4F4F6` on an 80 %-white plate.
     *
     * **A fill that describes a track is not a foreground**, and stays a literal:
     * `MediaSliderTrack` lightens *toward* white by a constant and `MediaVerticalSlider`
     * defaults its unplayed track to 20 % white, for which there is no token and should
     * not be one — a value used by a single slider is a constant, not a palette entry.
     *
     * **The sweep finished on 2026-09-03.** This used to read "`feature/viewer/` draws on
     * the scrims with `Color.White` literals instead — 58 of them at the last count".
     * ME-121 converted twelve, and ME-163, ME-170 and ME-171 took the rest across
     * twenty files. What is left is **five literals, every one of them documented where
     * it sits**: the PiP corner flash (the white *is* the effect), `MediaSliderTrack`'s
     * two `lerp` targets and `MediaVerticalSlider`'s unplayed track (a direction and a
     * constant, not foregrounds), and the subtitle swatch's unselected rim, which
     * separates a circle of the user's own colour from the panel and needs more than
     * `onScrimBorder`'s 0.08 to do it.
     *
     * So the count is not "58 undocumented" but **zero**. A new `Color.White` under
     * `feature/viewer/` is now either a mistake or a case that owes a comment.
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
