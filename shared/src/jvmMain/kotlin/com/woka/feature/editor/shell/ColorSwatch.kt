package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * The circle a colour row is made of — an offered value, or the rainbow that opens the
 * picker (ME-114).
 *
 * Its own file because `ParamRowColorAction` reached the size mandate when the custom
 * circle arrived, and because both circles have to be the *same* circle: two call sites
 * each spelling out a 24 dp clipped bordered box is how the offered swatches and the
 * custom one end up a pixel apart, which reads as a rendering fault rather than a choice.
 */

/** The rainbow the custom circle is filled with; the sweep closes on its first stop. */
internal val HUE_WHEEL = List(7) { Color.hsv(it * 60f % 360f, 0.9f, 1f) }

/**
 * One circle, whatever fills it.
 *
 * The ring is drawn **after** the fill: a border under a background is a border painted
 * over, which is the one-line bug this shape exists to stop repeating per swatch.
 */
@Composable
internal fun ColorSwatch(
    fill: Brush,
    ringed: Boolean,
    /** Null for a swatch that only *reports* a colour — the picker's readout. */
    onClick: (() -> Unit)? = null,
    tag: String? = null
) {
    Box(
        modifier = Modifier
            .size(EditorTokens.Inspector.swatchSize)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = 2.dp,
                color = if (ringed) Theme.colors.accentEditor else Color.Transparent,
                shape = CircleShape
            )
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                }
            )
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
    )
}

/** A flat colour as a `Brush`, so [ColorSwatch] takes one type rather than two. */
internal fun solidFill(argb: Long): Brush = SolidColor(Color(argb))
