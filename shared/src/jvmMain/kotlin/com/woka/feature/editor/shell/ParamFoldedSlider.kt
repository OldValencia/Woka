package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.DetentSlider
import com.woka.core.ui.components.DetentSliderColors
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet

/**
 * A level drawn **inside another control's panel** — the plate's `Corner` inside the
 * plate's own button (ME-155, `EditorTool.folded`).
 *
 * A slider rather than a `ParamValueStepper`, deliberately and for one reason: the stepper
 * opens a panel, and a panel opened from inside a panel is a stack the pointer cannot get
 * out of. Here there is already a card, which is the whole argument for folding the spec
 * in — so the control that needs room is the one that gets it.
 *
 * **Name and reading above, track below.** On one line it is a label, a track and a number
 * competing for the same width, and the card it sits in is as wide as a palette four
 * circles across — about 120 dp inside its padding, of which the track was getting thirty.
 * Stacked, the track has the whole width and the two words have their own line.
 */
@Composable
internal fun FoldedSlider(spec: FloatParam, params: ParamSet, writer: ParamWriter) {
    val value = params[spec]
    val span = spec.range.endInclusive - spec.range.start
    fun toValue(fraction: Float) = spec.range.start + fraction * span
    fun toFraction(v: Float) = if (span == 0f) 0f else (v - spec.range.start) / span

    Column(
        modifier = Modifier.fillMaxWidth().testTag("param-${spec.key}"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                text = spec.label,
                style = TextStyle(color = Theme.viewer.onScrimSecondary, fontSize = 11.sp)
            )
            BasicText(
                text = formatFloat(value, spec),
                modifier = Modifier.testTag("param-${spec.key}-readout"),
                style = TextStyle(color = Theme.viewer.onScrim, fontSize = 11.sp)
            )
        }
        DetentSlider(
            fraction = toFraction(value),
            colors = DetentSliderColors(
                track = Theme.viewer.onScrimHover,
                detent = Theme.viewer.onScrimBorder,
                knob = Theme.viewer.onScrim,
                fill = Theme.viewer.onScrim
            ),
            detents = listOf(toFraction(spec.detent)),
            gestureKey = spec.key,
            onTap = { writer.edit(spec, toValue(it), settled = true) },
            onDrag = { writer.edit(spec, toValue(it), settled = false) },
            onDragEnd = writer::settle,
            modifier = Modifier.testTag("param-${spec.key}-slider")
        )
    }
}
