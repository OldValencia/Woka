package com.woka.feature.viewer.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.ORIGINAL_TOOLTIP
import com.woka.core.ui.components.OriginalCompare
import com.woka.core.ui.components.originalPress
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

internal const val VIEWER_ORIGINAL_TAG = "viewer-original"

/**
 * `Original` in the viewer's pill, for a file that carries an edit (`#viewer-original`,
 * RD-502) — the editor's `Original` in the viewer's clothes, one [OriginalCompare] gesture.
 *
 * **`EditButton`'s geometry**, because the prototype draws both as one `.pill-edit` class and
 * the app's Edit already settled what that is here (24 dp, 13 sp). On, the glyph and the
 * word take the viewer's accent, with no fill (`#viewer-original[aria-pressed=true]`).
 */
@Composable
internal fun ViewerOriginalButton(compare: OriginalCompare) {
    val tint = if (compare.active) Theme.colors.accent else Theme.colors.textPrimary
    ViewerTooltip(ORIGINAL_TOOLTIP) {
        Row(
            modifier = Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .originalPress(compare)
                .padding(horizontal = 8.dp)
                .testTag(VIEWER_ORIGINAL_TAG),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(Icons.Static.PeekOriginal),
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp).size(12.dp),
                colorFilter = ColorFilter.tint(tint)
            )
            BasicText(
                text = "Original",
                style = TextStyle(color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.offset(y = (-1).dp)
            )
        }
    }
}
