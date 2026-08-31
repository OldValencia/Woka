package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.DetentSlider
import com.woka.core.ui.components.DetentSliderColors
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet

/**
 * What `− │ 64px ▾ │ +` opens: the slider the row used to be, a field the number can be
 * typed into, and `Reset` (ME-155, asked for 2026-08-29).
 *
 * **The slider did not disappear — it moved.** Everything a `FloatParamRow` could do is
 * still here and done the same way, including the detent mark and the one-history-entry
 * drag; what changed is that the 300 dp it needed is spent only while it is being used.
 *
 * The field is the part the panel never had. A caption at 137 px was previously reachable
 * only by landing a knob on one pixel of a track.
 */
@Composable
internal fun ParamValuePanel(spec: FloatParam, params: ParamSet, writer: ParamWriter) {
    ParamAccessory(
        key = "param-${spec.key}",
        tag = "param-${spec.key}-popover",
        // The field, its unit and `Reset` side by side, with a track above them long
        // enough to aim along — measured against those three, not chosen.
        width = 224.dp
    ) {
        ParamValueControls(spec, params, writer)
    }
}

/**
 * The three controls themselves, without the popover around them (ME-161).
 *
 * Split out when the chooser strip needed the same slider *inline*: a strip that showed
 * fifteen adjustments and then opened a popover to set one would put a card over a card
 * for a control the panel has room to draw. It emits no layout of its own, so both
 * callers place the slider and the field as direct children of their own column and the
 * popover's spacing is unchanged.
 */
@Composable
internal fun ParamValueControls(spec: FloatParam, params: ParamSet, writer: ParamWriter) {
    val value = params[spec]
    val span = spec.range.endInclusive - spec.range.start
    fun toValue(fraction: Float) = spec.range.start + fraction * span
    fun toFraction(v: Float) = if (span == 0f) 0f else (v - spec.range.start) / span

    Box(modifier = Modifier.fillMaxWidth()) {
        DetentSlider(
            fraction = toFraction(value),
            colors = DetentSliderColors(
                track = Theme.viewer.onScrimHover,
                detent = Theme.viewer.onScrimBorder,
                knob = Theme.viewer.onScrim,
                fill = Theme.viewer.onScrim
            ),
            detents = listOf(toFraction(spec.detent)),
            // Keyed on the spec for the reason `FloatControl` recorded before the
            // slider moved here: Markup swaps `Width` for `Text size` in the same
            // position, and a gesture that outlived the swap wrote the wrong key.
            gestureKey = spec.key,
            // **Thinner than the component's default** (reported by hand 2026-09-01).
            // This slider used to live in a 224 dp popover; ME-161 put it across a
            // ~390 dp card, where 3 dp of full-strength `onScrim` reads as a stripe
            // painted over the panel rather than as a track with a knob on it.
            trackHeight = INLINE_TRACK_HEIGHT,
            onTap = { writer.edit(spec, toValue(it), settled = true) },
            onDrag = { writer.edit(spec, toValue(it), settled = false) },
            onDragEnd = writer::settle,
            modifier = Modifier.testTag("param-${spec.key}-slider")
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ValueField(spec, value, writer)
        ResetButton(
            enabled = value != spec.detent,
            tag = "param-${spec.key}-reset",
            onClick = { writer.resetTo(spec) }
        )
    }
}

/** Two, not the component's three: the card is nearly twice the popover's width. */
private val INLINE_TRACK_HEIGHT = 2.dp
