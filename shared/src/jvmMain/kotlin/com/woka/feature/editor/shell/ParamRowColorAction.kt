package com.woka.feature.editor.shell

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.ParamSet

/**
 * The two rows that are neither a level nor a choice among named options: picking a
 * colour, and performing something. Split from `ParamRowChoice` at 150 lines, along the
 * line that already separated them — an enum picker reads a value, these two set one
 * directly. The circle they are drawn from is `ColorSwatch`.
 */

/**
 * Swatches, with the accent ring marking the chosen one, and a rainbow circle that opens
 * the picker (ME-114).
 *
 * The stored value is deliberately **not** restricted to the offered set — the custom
 * popover exists precisely to leave it. A value matching no swatch therefore rings the
 * rainbow circle instead, so a hand-picked colour shows *where* it came from rather than
 * leaving the whole row looking unselected.
 */
@Composable
internal fun ColorParamRow(spec: ColorParam, params: ParamSet, writer: ParamWriter) {
    val selected = params[spec]
    var picking by remember { mutableStateOf(false) }

    ParamRowFrame(spec.label) {
        Row(
            modifier = Modifier.weight(1f).testTag("param-${spec.key}"),
            horizontalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.swatchGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            spec.swatches.forEachIndexed { index, argb ->
                ColorSwatch(
                    // `swatchFill`, not `solidFill`: a swatch with no alpha is an offered
                    // "none" and draws as a slash rather than as nothing (ME-146).
                    fill = swatchFill(argb),
                    ringed = argb == selected,
                    onClick = { writer.edit(spec, argb) },
                    // Indexed rather than named by value: a test says "the fifth swatch",
                    // and the palette is free to change without rewriting the test.
                    tag = "param-${spec.key}-swatch-$index"
                )
            }
            ColorSwatch(
                fill = Brush.sweepGradient(HUE_WHEEL),
                ringed = !selected.isSwatch(spec.swatches),
                onClick = { picking = true },
                tag = "param-${spec.key}-custom"
            )
        }
    }

    if (picking) {
        ColorPickerPopover(
            argb = selected,
            // Unsettled on every event of the drag and settled once on dismiss, so
            // sliding through fifty colours is **one** history entry — the rule
            // `ParamWriter` already applies to a slider, reused rather than restated.
            onPick = { argb -> writer.edit(spec, argb, settled = false) },
            onDismiss = {
                picking = false
                writer.settle()
            }
        )
    }
}

/**
 * Icon buttons. Always `Instant` by the spec's own contract, so a press is a commit and
 * two presses are two operations — which is the manifest's `Repeating` cardinality doing
 * the work, not anything here.
 */
@Composable
internal fun ActionParamRow(spec: ActionParam, writer: ParamWriter) {
    // **No label column.** An action's label is written on the button that performs
    // it, so putting it in the row's label column too printed "Rotate left" twice on
    // the same line — and left the button stranded in a 420 dp card with a column of
    // white space between it and everything else.
    BasicText(
        text = spec.label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { writer.edit(spec, true) }
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("param-${spec.key}"),
        style = TextStyle(
            color = Theme.viewer.onScrim,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
}
