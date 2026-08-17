package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.SegmentedControl
import com.woka.core.ui.components.SegmentedControlDefaults
import com.woka.core.ui.theme.Theme
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
                    options = spec.options.map { it.name },
                    selected = selected.name,
                    onSelect = { name ->
                        spec.options.firstOrNull { it.name == name }?.let { writer.edit(spec, it) }
                    },
                    colors = SegmentedControlDefaults.onScrim
                )
            } else {
                ChipRow(spec, selected, writer)
            }
        }
    }
}

private const val SEGMENTED_LIMIT = 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <E : Enum<E>> ChipRow(spec: ChoiceParam<E>, selected: E, writer: ParamWriter) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        spec.options.forEach { option ->
            val isSelected = option == selected
            BasicText(
                text = option.name,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isSelected) Theme.viewer.onScrimSelected else Theme.viewer.onScrimHover
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { writer.edit(spec, option) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                style = TextStyle(
                    color = if (isSelected) Theme.colors.accentEditor else Theme.viewer.onScrimSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
