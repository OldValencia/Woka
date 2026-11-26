package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

/** A toggle, alone: a well of one, as a level is (RD-302). */
@Composable
internal fun BoolParamRow(spec: BoolParam, params: ParamSet, writer: ParamWriter) {
    ScrubWell(listOf(spec), params, writer)
}
