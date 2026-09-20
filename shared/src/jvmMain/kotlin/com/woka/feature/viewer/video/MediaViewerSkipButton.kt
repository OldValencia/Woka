package com.woka.feature.viewer.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * `iconWidth`/`iconHeight` set the drawn glyph independently — the SVG is stretched
 * to fill them (`ContentScale.FillBounds`), so non-equal values distort the shape on
 * purpose. `hitPadding` is the extra clickable margin around it, so the click area is
 * icon + 2×padding: the box wraps the padded icon, which is why the icon size is
 * never clamped by it.
 */
private val ICON_WIDTH = 20.dp
private val ICON_HEIGHT = 23.dp
private val HIT_PADDING = 4.dp

/**
 * One of the ±5s buttons flanking the seek bar. Both were the same seventeen lines
 * written twice inside [MediaViewerTimeSlider]; split out under the §2 file-size
 * mandate when the preview wiring needed the room.
 */
@Composable
internal fun SkipButton(
    icon: DrawableResource,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(Theme.colors.textPrimary),
            modifier = Modifier.padding(HIT_PADDING).width(ICON_WIDTH).height(ICON_HEIGHT)
        )
    }
}
