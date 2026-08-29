package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToggleSetParam

/**
 * A line the tool asked for: its controls side by side, **with no label column at all**
 * (ME-155).
 *
 * This is what replaced ME-153's paired line. That one kept both names and put two
 * controls under them, and it did not work — a slider beside buttons is the shape that was
 * refused by hand. Here every control is collapsed to a button that reports its own value,
 * so there is nothing left for a label to explain: a swatch is a colour, `64px` is a size,
 * `Aa` in bold is a weight.
 *
 * A `FlowRow` rather than a `Row` so a narrow window wraps the bar instead of laying its
 * last button out at zero width — the failure a swatch strip already produced once when it
 * was given a `weight` (ME-153).
 *
 * The `when` is deliberately **not** exhaustive, unlike `ParamRow`'s: that one asks "can
 * this spec be edited at all", and a missing branch there is a value nobody could ever
 * change. This asks "does this spec have a compact form", and for a preview strip or a pad
 * the honest answer is no — they are not drawn here rather than drawn badly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompactRow(
    run: List<ParamSpec<*>>,
    params: ParamSet,
    writer: ParamWriter,
    /** What rides inside a given spec's popover instead of beside it — `EditorTool.folded`. */
    folded: (ParamSpec<*>) -> List<ParamSpec<*>> = { emptyList() }
) {
    FlowRow(
        modifier = Modifier.padding(vertical = EditorTokens.Inspector.barGap),
        horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
        verticalArrangement = Arrangement.spacedBy(CONTROL_GAP),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        run.forEach { spec ->
            key(spec.key) { CompactControl(spec, params, writer, folded(spec)) }
        }
    }
}

@Composable
private fun CompactControl(
    spec: ParamSpec<*>,
    params: ParamSet,
    writer: ParamWriter,
    folded: List<ParamSpec<*>>
) {
    when (spec) {
        is ChoiceParam<*> -> ChoiceControl(spec, params, writer, Modifier)
        is ColorParam -> ColorCompact(spec, params, writer, folded)
        is FloatParam -> FloatControl(spec, params, writer)
        is ToggleSetParam<*> -> FacesCompact(spec, params, writer)
        else -> Unit
    }
}

/**
 * Air between two controls of the bar.
 *
 * **Ten, not the chip row's six** (asked for 2026-08-29). Chips are one control and read
 * as a group at six; these are four separate controls that each open something, and at six
 * they read as one crowded strip.
 */
private val CONTROL_GAP = 10.dp
