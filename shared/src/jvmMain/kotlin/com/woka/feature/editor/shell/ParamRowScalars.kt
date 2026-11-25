package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.woka.core.ui.components.SliderSwitch
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ParamSet
import kotlin.math.roundToInt

/**
 * A level, alone (RD-202): a scrub row in its own well. The stepper with its popover
 * (ME-155) is now only the compact form, [FloatControl], which Markup's bars still use.
 */
@Composable
internal fun FloatParamRow(spec: FloatParam, params: ParamSet, writer: ParamWriter) {
    // A well of one (RD-202): the scrub row is the inspector's form for a level.
    ScrubWell(listOf(spec), params, writer)
}

/**
 * The stepper frame, so a compact line can put it beside a swatch with no label column at
 * all (ME-155).
 */
@Composable
internal fun FloatControl(spec: FloatParam, params: ParamSet, writer: ParamWriter) {
    val value = params[spec]
    val slot = rememberAccessorySlot("param-${spec.key}")
    val step = stepOf(spec)

    ParamValueStepper(
        text = formatFloat(value, spec),
        name = spec.label,
        open = slot.open,
        canStepDown = value > spec.range.start,
        canStepUp = value < spec.range.endInclusive,
        tag = "param-${spec.key}",
        onStepDown = { writer.edit(spec, (value - step).coerceIn(spec.range)) },
        onStepUp = { writer.edit(spec, (value + step).coerceIn(spec.range)) },
        onToggle = {
            // Settled on the way out, so sliding through fifty values inside the panel is
            // one history entry — the rule `ColorPickerPopover` already applies.
            if (slot.open) writer.settle()
            slot.toggle()
        },
        onReset = { writer.resetTo(spec) }
    )

    if (slot.open) {
        ParamValuePanel(spec, params, writer)
    }
}

/**
 * What one press of an end is worth.
 *
 * One unit on the spans the editor actually uses — `-100..100` — and five on the one that
 * is an order of magnitude wider, because 64 px to 128 px is sixty-four presses at one.
 * Anything further from the current value than a few nudges is what the slider and the
 * field are for.
 *
 * **A spec may name its own instead**, and markup's two sizes do: their range is one wide
 * constant while their default follows the picture (ME-159), so the span stopped being a
 * usable proxy for what a nudge should be worth.
 */
private fun stepOf(spec: FloatParam): Float =
    spec.step ?: if (spec.range.endInclusive - spec.range.start > 200f) 5f else 1f

/**
 * Signed only when the range crosses zero.
 *
 * A brightness of `+12` says which way it was moved; a size of `+1280` says nothing
 * and looks like an error.
 */
internal fun formatFloat(value: Float, spec: FloatParam): String =
    plainFloat(value).let { shown ->
        val bipolar = spec.range.start < 0f && spec.range.endInclusive > 0f
        val sign = if (bipolar && value > 0f) "+" else ""
        sign + shown + (spec.unit ?: "")
    }

/** The number with no sign and no unit — what a field is edited in. */
internal fun plainFloat(value: Float): String {
    val rounded = (value * 100).roundToInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}" else "$rounded"
}

/** A number with steppers. The unit is a suffix on the value, never its own column. */
@Composable
internal fun IntParamRow(spec: IntParam, params: ParamSet, writer: ParamWriter) {
    val value = params[spec]
    ParamRowFrame(spec.label) {
        Box(modifier = Modifier.weight(1f)) {
            IntStepper(
                value = value,
                range = spec.range,
                testTag = "param-${spec.key}",
                onChange = { writer.edit(spec, it) }
            )
        }
    }
}

@Composable
internal fun BoolParamRow(spec: BoolParam, params: ParamSet, writer: ParamWriter) {
    ParamRowFrame(spec.label) {
        Box(modifier = Modifier.weight(1f)) {
            SliderSwitch(
                checked = params[spec],
                onCheckedChange = { writer.edit(spec, it) },
                colors = RoomControls.switch,
                modifier = Modifier.testTag("param-${spec.key}")
            )
        }
    }
}
