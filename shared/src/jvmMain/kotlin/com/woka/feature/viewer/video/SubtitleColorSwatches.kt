package com.woka.feature.viewer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * The subtitle colour palette and the row that offers it.
 *
 * Its own file since ME-171, and for the reason the ticket exists: this is the one
 * control in the customizer whose foreground is **not** drawn on the panel's scrim —
 * a swatch is filled with the user's own colour — so it is the one that had to keep a
 * literal. Sitting beside the pills and the stepper it read as an oversight; alone with
 * the note explaining it, it does not. Splitting it also put
 * `SubtitleCustomizeControls.kt` back under the size mandate, which that note had
 * pushed it over.
 */

/** Preset text colours offered in the subtitle customizer (0xAARRGGBB). */
val subtitleColorSwatches = listOf(
    0xFFFFFFFFL, 0xFFFFF04DL, 0xFF4DD2FFL, 0xFF6EE86EL, 0xFFFF7AB2L, 0xFFFF5A4DL
)

@Composable
fun SubtitleColorSwatchRow(selectedArgb: Long, onSelect: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        subtitleColorSwatches.forEach { argb ->
            val isSelected = argb == selectedArgb
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(argb.toSubtitleColor())
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        // **The unselected ring stays a literal** (ME-171). It is not a
                        // foreground on the scrim — it is the rim of a swatch filled with the
                        // user's own subtitle colour, and its job is to separate that circle
                        // from the panel whether the circle is white or black. `onScrimBorder`
                        // is 0.08, four times fainter, and a white swatch would lose its edge.
                        color = if (isSelected) Theme.colors.accent else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelect(argb) }
            )
        }
    }
}
