package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

/** How far the fill pulls away from the ring, so the two read as separate rings. */
private val RING_GAP = 4.dp

/**
 * One circle, whatever fills it.
 *
 * **The ring sits outside the colour, not on it** (asked for 2026-08-26). Drawn as a
 * border on the swatch itself it was a 2 dp line *inside* a saturated circle, which on
 * orange or green is nearly invisible — the selected colour looked no different from the
 * six beside it. So the ring is the outer edge and the fill shrinks away from it by
 * [RING_GAP], which is the iOS shape and, unlike growing the ring outward, keeps every
 * swatch the same size and the row the same height whichever one is chosen.
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
            .then(if (tag == null) Modifier else Modifier.testTag(tag)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Only when ringed: an unselected swatch is the full circle, so the row
                // does not look like six holes with one colour in it.
                .padding(if (ringed) RING_GAP else 0.dp)
                .clip(CircleShape)
                .background(fill)
        )
    }
}

/** A flat colour as a `Brush`, so [ColorSwatch] takes one type rather than two. */
internal fun solidFill(argb: Long): Brush = SolidColor(Color(argb))

/**
 * The circle a **row** is filled with: a colour, or the "none" slash when that colour has
 * no alpha at all (ME-146).
 *
 * `0L` is a legal `ColorValue` and it is how markup's plate says "no plate", so it has to
 * be drawable rather than invisible — six filled circles beside one that is not there
 * reads as a rendering fault. Hard stops rather than a fade, and no explicit start and end
 * offsets: `linearGradient` defaults to corner-to-corner, so the band comes out diagonal
 * by construction.
 *
 * Fixed whites rather than `Theme`, for the reason the whole row is drawn in on-scrim
 * colours: this sits over a photograph, not over a surface, and the pair is light in both
 * themes. It also keeps this a plain function rather than a composable one, which
 * [solidFill] beside it already is.
 */
internal fun swatchFill(argb: Long): Brush =
    if ((argb ushr 24) != 0L) solidFill(argb) else Brush.linearGradient(*NONE_SLASH)

private val NONE_SLASH = arrayOf(
    0f to Color.White.copy(alpha = 0.12f),
    0.46f to Color.White.copy(alpha = 0.12f),
    0.46f to Color.White.copy(alpha = 0.75f),
    0.54f to Color.White.copy(alpha = 0.75f),
    0.54f to Color.White.copy(alpha = 0.12f),
    1f to Color.White.copy(alpha = 0.12f)
)

/**
 * What colour the letter on a plate is drawn in.
 *
 * Measured rather than fixed: a black `A` vanishes on the black swatch and a white one on
 * the yellow. `0L` is the plate that is not there, and its slash is drawn light, so the
 * letter goes dark over it.
 */
internal fun inkOn(argb: Long): Color {
    if ((argb ushr 24) == 0L) return Color.Black.copy(alpha = 0.55f)
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    return if (luminance > 0.55f) Color.Black.copy(alpha = 0.8f) else Color.White
}

/** The one letter that reads at 10 sp and says "type sits on this". */
internal const val PLATE_SAMPLE = "A"
