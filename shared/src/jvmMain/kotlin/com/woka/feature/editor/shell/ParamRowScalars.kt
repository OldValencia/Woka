package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.woka.core.ui.components.DetentSlider
import com.woka.core.ui.components.DetentSliderColors
import com.woka.core.ui.components.IosSwitch
import com.woka.core.ui.components.SwitchDefaults
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ParamSet
import kotlin.math.roundToInt

/**
 * A slider row.
 *
 * **White, not the accent.** The accent marks *selection*; a level is not a selection,
 * and the zoom slider already established the language.
 *
 * The knob is dragged in a 0..1 fraction and mapped both ways here — `DetentSlider`
 * knows nothing about ranges, which is what lets the same widget serve zoom, speed and
 * every editor parameter.
 */
@Composable
internal fun FloatParamRow(spec: FloatParam, params: ParamSet, writer: ParamWriter) {
    ParamRowFrame(spec.label) {
        FloatControl(spec, params, writer, Modifier.weight(1f))
    }
}

/**
 * The slider and its readout, without a row of their own — so a paired line can put them
 * beside another control under the tool's own second label (ME-153).
 */
@Composable
internal fun RowScope.FloatControl(
    spec: FloatParam,
    params: ParamSet,
    writer: ParamWriter,
    modifier: Modifier
) {
    val value = params[spec]
    val span = spec.range.endInclusive - spec.range.start
    fun toValue(fraction: Float) = spec.range.start + fraction * span
    fun toFraction(v: Float) = if (span == 0f) 0f else (v - spec.range.start) / span

    Box(modifier = modifier) {
        DetentSlider(
            fraction = toFraction(value),
            colors = DetentSliderColors(
                track = Theme.viewer.onScrimHover,
                detent = Theme.viewer.onScrimBorder,
                knob = Theme.viewer.onScrim,
                fill = Theme.viewer.onScrim
            ),
            detents = listOf(toFraction(spec.detent)),
            // **Keyed on the spec, not only on the track's width.** A tool may swap one
            // row for another in the same position — Markup trades `Width` for
            // `Text size` with the pen — and the gesture must go with it. Without this
            // the node is reused, its `pointerInput` keys are unchanged, and the
            // coroutine still running closes over the spec it was built with: the row
            // reads "Text size" and writes `width` (found by hand 2026-08-26).
            gestureKey = spec.key,
            onTap = { writer.edit(spec, toValue(it), settled = true) },
            onDrag = { writer.edit(spec, toValue(it), settled = false) },
            onDragEnd = writer::settle,
            modifier = Modifier.testTag("param-${spec.key}")
        )
    }
    Readout(text = formatFloat(value, spec), tag = "param-${spec.key}-readout")
}

/**
 * Signed only when the range crosses zero.
 *
 * A brightness of `+12` says which way it was moved; a size of `+1280` says nothing
 * and looks like an error.
 */
internal fun formatFloat(value: Float, spec: FloatParam): String {
    val rounded = (value * 100).roundToInt() / 100f
    val shown = if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}" else "$rounded"
    val bipolar = spec.range.start < 0f && spec.range.endInclusive > 0f
    val sign = if (bipolar && rounded > 0f) "+" else ""
    return sign + shown + (spec.unit ?: "")
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
            IosSwitch(
                checked = params[spec],
                onCheckedChange = { writer.edit(spec, it) },
                colors = SwitchDefaults.onScrim,
                modifier = Modifier.testTag("param-${spec.key}")
            )
        }
    }
}
