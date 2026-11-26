package com.woka.core.ui.components.frames

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.woka.core.ui.theme.Theme

/**
 * The frame strip's colours (RD-204, `shell.css` `.frame-column`). One strip in two places:
 * on glass over the viewer's media, on the room's panel in the editor — the shape and the
 * place are the same, so entering the editor recolours the strip and does not move it.
 */
@Immutable
data class FrameStripColors(
    /** The card behind the column. */
    val card: Color,
    /** Its 1 dp outline. */
    val line: Color,
    /** A tile while its picture loads. */
    val tile: Color,
    /** The selected tile's 2 dp inner border. */
    val selected: Color
)

object FrameStripDefaults {

    /** The viewer's: glass over the picture, the app's accent on the open frame. */
    val viewer: FrameStripColors
        @Composable get() = FrameStripColors(
            card = Theme.colors.glass,
            line = Theme.colors.border,
            tile = Theme.colors.surfaceRaised,
            selected = Theme.colors.accent
        )
}
