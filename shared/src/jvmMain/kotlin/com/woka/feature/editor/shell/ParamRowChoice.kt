package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.woka.core.ui.components.SegmentedControl
import com.woka.core.ui.components.SegmentedControlDefaults
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ParamSet

/**
 * A `SegmentedControl` at four options or fewer, a wrapping chip row above that
 * (§3.4).
 *
 * Four is not a guess: the segmented control divides its width evenly, so a fifth
 * option is a fifth of 420 dp minus padding — narrower than the word "Original".
 *
 * The value is carried as the constant's **name**, never its ordinal, which is what
 * `ChoiceParam.encode` does too: an ordinal makes reordering the enum a silent rewrite
 * of every document already saved.
 *
 * **The chips themselves are `ParamRowChip`** since ME-147, where a second spec needed
 * the same ones. What moved is the drawing, including the one-scale-per-row glyph sizing;
 * what stayed is this row's own question, which is "one of these" — a toggle set asks a
 * different one and calls the same chip.
 */
@Composable
internal fun <E : Enum<E>> ChoiceParamRow(
    spec: ChoiceParam<E>,
    params: ParamSet,
    writer: ParamWriter
) {
    val selected = params[spec]
    ParamRowFrame(spec.label) {
        Box(modifier = Modifier.weight(1f).testTag("param-${spec.key}")) {
            if (spec.options.size <= SEGMENTED_LIMIT) {
                SegmentedControl(
                    options = spec.options.map(spec.labelOf),
                    selected = spec.labelOf(selected),
                    onSelect = { label ->
                        spec.options.firstOrNull { spec.labelOf(it) == label }
                            ?.let { writer.edit(spec, it) }
                    },
                    colors = SegmentedControlDefaults.onScrim
                )
            } else {
                ParamChipRow(
                    spec.options.map { option ->
                        ParamChip(
                            tag = "param-${spec.key}-${option.name}",
                            label = spec.labelOf(option),
                            icon = spec.iconOf(option),
                            selected = option == selected,
                            onClick = { writer.edit(spec, option) }
                        )
                    }
                )
            }
        }
    }
}

private const val SEGMENTED_LIMIT = 4
