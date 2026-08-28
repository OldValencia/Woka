package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.DetentSlider
import com.woka.core.ui.components.DetentSliderColors
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.PreviewChoiceParam

/** Large enough to read a look off, small enough that a line of them fits a panel. */
internal val THUMBNAIL = 56.dp
private val THUMBNAIL_RADIUS = 8.dp

/**
 * One option of a preview strip: the picture through that option, its name, and — when
 * it is the chosen one — **its strength, inside the square** (asked for 2026-08-28).
 *
 * The strength used to be a full-width row under the strip. It meant nothing until
 * something was chosen and nothing at all on the option that *is* the absence of the
 * effect, so it sat there doing neither for most of a session. Inside the square it
 * costs no height, and it appears exactly where the thing it scales is.
 *
 * **56 dp stays 56 dp when selected** (settled 2026-08-28). Growing the chosen square
 * would give the slider twice the travel, and would make the line jump under the cursor
 * on every click; ~2 % per pixel is coarse but a style's strength is not a number
 * anybody dials to the unit.
 */
@Composable
internal fun <E : Enum<E>> PreviewChoiceOption(
    spec: PreviewChoiceParam<E>,
    option: E,
    params: ParamSet,
    writer: ParamWriter,
    isSelected: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    val outline = if (isSelected) Theme.colors.accentEditor else Theme.viewer.onScrimBorder
    Column(
        modifier = Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { writer.edit(spec, option) }
            .testTag("param-${spec.key}-${option.name}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(THUMBNAIL)) {
            content(
                Modifier
                    .size(THUMBNAIL)
                    .clip(RoundedCornerShape(THUMBNAIL_RADIUS))
                    .background(Theme.viewer.onScrimHover)
                    .border(1.dp, outline, RoundedCornerShape(THUMBNAIL_RADIUS))
            )
            // **Not on the default option.** `Neutral` and `Original` are the absence
            // of an effect, so scaling them scales nothing — the same rule that keeps
            // `Fill` off a stroked pen's panel.
            val strength = spec.strength?.takeIf { isSelected && option != spec.default }
            if (strength != null) {
                StrengthSlider(strength, params, writer, Modifier.align(Alignment.BottomCenter))
            }
        }
        BasicText(
            text = spec.labelOf(option),
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(
                color = if (isSelected) Theme.colors.accentEditor else Theme.viewer.onScrimSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/**
 * The strength, as a bar across the bottom of its own square.
 *
 * A scrim under it rather than none: the track sits on a photograph that may be any
 * colour, and a white knob on a white subject is a control nobody can find.
 */
@Composable
private fun StrengthSlider(
    spec: FloatParam,
    params: ParamSet,
    writer: ParamWriter,
    modifier: Modifier
) {
    val span = spec.range.endInclusive - spec.range.start
    fun toValue(fraction: Float) = spec.range.start + fraction * span
    fun toFraction(value: Float) = if (span == 0f) 0f else (value - spec.range.start) / span

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(STRENGTH_HEIGHT)
            .background(Theme.viewer.popoverScrim)
            .padding(horizontal = STRENGTH_INSET),
        contentAlignment = Alignment.Center
    ) {
        DetentSlider(
            fraction = toFraction(params[spec]),
            colors = DetentSliderColors(
                track = Theme.viewer.onScrimBorder,
                detent = Theme.viewer.onScrimBorder,
                knob = Theme.viewer.onScrim,
                fill = Theme.viewer.onScrim
            ),
            detents = listOf(toFraction(spec.detent)),
            gestureKey = spec.key,
            onTap = { writer.edit(spec, toValue(it), settled = true) },
            onDrag = { writer.edit(spec, toValue(it), settled = false) },
            onDragEnd = writer::settle,
            modifier = Modifier.testTag("param-${spec.key}")
        )
    }
}

private val STRENGTH_HEIGHT = 18.dp
private val STRENGTH_INSET = 4.dp
