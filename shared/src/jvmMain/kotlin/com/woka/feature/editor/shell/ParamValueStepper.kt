package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip

/**
 * `−  16  +` inside one rounded field — the control every number in the editor is set
 * with (ME-155, drawn by hand as `img.png` and matched to it).
 *
 * **Three gestures on one 114 dp frame.** The ends nudge and clamp, which is what a value
 * a few steps from right wants; the middle opens the slider, the typed field and `Reset`,
 * which is what a value far from right wants; a right-click anywhere on it puts the value
 * back. The inline slider it replaces could only do the middle of those, and spent 300 dp
 * of a card drawn over a photograph doing it.
 *
 * **No chevron on the number** (2026-08-29). It was there to announce the popover and
 * read as a stray glyph beside the digits — `0 ∨` — in a field the mockup draws as a
 * plain number. The hover fill and the hand cursor carry the affordance instead, which is
 * what the mockup already relied on.
 *
 * Purely presentational — handed a string and four lambdas, and knowing nothing about
 * specs, so a labelled row and a compact bar share one frame rather than each drawing
 * their own a pixel apart.
 */
@Composable
internal fun ParamValueStepper(
    text: String,
    /** What this number *is* — the compact bar has no label column to say it. */
    name: String,
    open: Boolean,
    canStepDown: Boolean,
    canStepUp: Boolean,
    tag: String,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit
) {
    ViewerTooltip(name) {
        Row(
            modifier = Modifier
                .height(STEPPER_HEIGHT)
                .clip(RoundedCornerShape(STEPPER_RADIUS))
                .background(Theme.viewer.onScrimHover)
                .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(STEPPER_RADIUS))
                .resetOnRightClick(onReset)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepEnd("−", "$tag-minus", canStepDown, onStepDown)
            Box(
                modifier = Modifier
                    .width(VALUE_WIDTH)
                    .height(STEPPER_HEIGHT)
                    .background(if (open) Theme.viewer.onScrimSelected else Color.Transparent)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onToggle)
                    .testTag("$tag-value"),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = text,
                    // The readout column's old tag, kept: the number moved inside the
                    // frame but it is still "what this parameter currently says", which
                    // is what every test asking for it means (ME-155).
                    modifier = Modifier.testTag("$tag-readout"),
                    style = TextStyle(
                        color = Theme.viewer.onScrim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                )
            }
            StepEnd("+", "$tag-plus", canStepUp, onStepUp)
        }
    }
}

/**
 * One end of the frame. Disabled rather than clamping: a `−` that accepts presses at the
 * floor and writes the same value back is a button that looks alive and is not — the rule
 * `IntStepper` settled and this inherits.
 */
@Composable
private fun StepEnd(symbol: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = STEP_WIDTH, height = STEPPER_HEIGHT)
            .then(
                if (enabled) {
                    Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = symbol,
            style = TextStyle(
                color = if (enabled) Theme.viewer.onScrim else Theme.viewer.onScrimDisabled,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

internal val STEPPER_HEIGHT = 26.dp
internal val STEPPER_RADIUS = 8.dp
private val STEP_WIDTH = 28.dp

/** Wide enough for `−100` and for `512px`, so the frame does not resize as it is used. */
private val VALUE_WIDTH = 58.dp
