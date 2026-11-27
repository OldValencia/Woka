package com.woka.feature.viewer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.slider.MediaSliderStyle
import com.woka.feature.viewer.shell.slider.defaultMediaSliderStyle

/** `.viewer-playback` (`viewer.css`), measured for RD-501. */
object TransportTokens {
    val barHeight = 48.dp
    val barRadius = 16.dp
    val barPadding = 12.dp
    val gap = 10.dp
    /** `.viewer-playback .icon-button`: a 28 dp target, an 18 dp glyph. */
    val button = 28.dp
    val glyph = 18.dp
    /** The leading label's floor, so the seek does not move as the digits change. */
    val timeMinWidth = 60.dp
    /** `box-shadow: 0 2px 18px` at 6.7 %. */
    val shadow = 9.dp
}

/**
 * One transport in two rooms (RD-501): glass over the viewer's media, the room's panel under
 * a GIF in the editor — the editor orange for Loop and the played part, the timeline's knob.
 */
@Immutable
data class TransportColors(
    val bar: Color,
    /** The 1 dp outline the room draws instead of the viewer's shadow; null for none. */
    val border: Color?,
    val text: Color,
    val loopOff: Color,
    val loopOn: Color,
    val loopOnFill: Color,
    val knob: PlayKnobColors,
    val slider: MediaSliderStyle
)

@Composable
fun viewerTransport(): TransportColors = TransportColors(
    bar = Theme.colors.glass,
    border = null,
    text = Theme.colors.textPrimary,
    loopOff = Theme.colors.textSecondary,
    loopOn = Theme.colors.accent,
    loopOnFill = Theme.colors.accentSoft,
    knob = viewerPlayKnob(),
    slider = defaultMediaSliderStyle()
)

@Composable
fun roomTransport(): TransportColors = TransportColors(
    bar = Theme.room.panel,
    border = Theme.room.line,
    text = Theme.room.text,
    loopOff = Theme.room.muted,
    loopOn = Theme.room.accentText,
    loopOnFill = Theme.room.accentFill,
    knob = roomPlayKnob(),
    slider = defaultMediaSliderStyle().copy(activeColor = Theme.room.accent, thumbColor = Theme.room.accent)
)

/** The bar's own box: height, shape, fill, and a shadow or an outline. */
fun Modifier.transportBar(colors: TransportColors): Modifier {
    val shape = RoundedCornerShape(TransportTokens.barRadius)
    val outlined = colors.border?.let { Modifier.border(1.dp, it, shape) } ?: Modifier.shadow(
        TransportTokens.shadow,
        shape,
        clip = false,
        spotColor = Color.Black.copy(alpha = 0.067f)
    )
    return this
        .height(TransportTokens.barHeight)
        .then(outlined)
        .clip(shape)
        .background(colors.bar)
        .padding(horizontal = TransportTokens.barPadding)
}
