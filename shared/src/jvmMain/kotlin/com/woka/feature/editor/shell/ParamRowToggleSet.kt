package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToggleSetParam

/**
 * The row for a `ToggleSetParam` — the same chips `ChoiceParamRow` draws above four
 * options, lighting however many of them are on (ME-147).
 *
 * **No segmented fallback.** A `SegmentedControl` shows exactly one selection, so at two
 * or three options it would draw this spec's value wrong rather than compactly; chips are
 * the only shape that can say "these two, not that one".
 *
 * **Every press is settled**, so each is its own history entry — a chip is a click and
 * there is no gesture to be in the middle of, which is the same answer a segment and a
 * switch give (`ParamWriter`). Four faces turned on one after another are four undo steps,
 * and each of them is a change the user made deliberately.
 */
@Composable
internal fun <E : Enum<E>> ToggleSetParamRow(
    spec: ToggleSetParam<E>,
    params: ParamSet,
    writer: ParamWriter
) {
    val on = params[spec]
    ParamRowFrame(spec.label) {
        Box(modifier = Modifier.weight(1f).testTag("param-${spec.key}")) {
            ParamChipRow(
                spec.options.map { option ->
                    ParamChip(
                        tag = "param-${spec.key}-${option.name}",
                        label = spec.labelOf(option),
                        icon = spec.iconOf(option),
                        selected = option in on,
                        // Written back through the spec, so the set reaches the document
                        // in `options` order however this one was built.
                        onClick = { writer.edit(spec, if (option in on) on - option else on + option) }
                    )
                }
            )
        }
    }
}
