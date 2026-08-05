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
