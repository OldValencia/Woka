package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec

/**
 * What a compact colour button opens: the offered colours, the hand-picker behind the
 * rainbow, and whatever the tool folded in beside them (ME-155).
 *
 * Split from `ParamColorCompact` at the size mandate, along the seam that was already
 * there — the button that *reports* a colour on that side, the card that *changes* it on
 * this one.
 *
 * **One card with two faces.** Pressing the rainbow grows this card downward rather than
 * raising a second one — the palette and the hand-picker are two ways of saying the same
 * value, and a picker that covered the swatches it was reached from would hide the answer
 * to "what was it before".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorPalettePanel(
    spec: ColorParam,
    params: ParamSet,
    writer: ParamWriter,
    folded: List<ParamSpec<*>>
) {
    val selected = params[spec]
    var custom by remember { mutableStateOf(false) }

    ParamAccessory(
        key = "param-${spec.key}",
        tag = "param-${spec.key}-popover",
        // **Four to a line** (asked for 2026-08-29). One line of everything made a card
        // wider than the panel it hangs off, and left a single circle stranded on a second
        // line when it did not fit; a square block reads as a palette.
        width = (PER_LINE * SWATCH + (PER_LINE - 1) * SWATCH_GAP + CARD_PADDING).dp
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.swatchGap),
            verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.swatchGap)
        ) {
            spec.swatches.forEachIndexed { index, argb ->
                ColorSwatch(
                    fill = swatchFill(argb),
                    ringed = argb == selected,
                    onClick = { writer.edit(spec, argb) },
                    // Indexed rather than named by value: a test says "the second
                    // swatch", and the palette is free to change without rewriting it.
                    tag = "param-${spec.key}-swatch-$index"
                )
            }
            ColorSwatch(
                fill = Brush.sweepGradient(HUE_WHEEL),
                ringed = custom || !selected.isSwatch(spec.swatches),
                onClick = { custom = !custom },
                tag = "param-${spec.key}-custom"
            )
        }
        if (custom) {
            HandPicker(selected) { argb -> writer.edit(spec, argb, settled = false) }
        }
        // What the tool said belongs here rather than beside the button — `Corner` inside
        // `Fill`, and nothing else today (`EditorTool.folded`).
        folded.forEach { extra ->
            key(extra.key) {
                if (extra is FloatParam) FoldedSlider(extra, params, writer)
            }
        }
    }
}

/** How many circles a line holds — the palettes are seven and eight, so four is two lines. */
private const val PER_LINE = 4

/**
 * `ColorSwatch`'s own size and the air between two of them.
 *
 * **Spelled as size plus gaps, not as a pitch** (measured 2026-08-29). Written as `n × 32
 * − 8` it is a gap short of nothing and a reader has to check which; written this way the
 * arithmetic is the row it describes.
 */
private const val SWATCH = 24
private const val SWATCH_GAP = 8

/** `EditorTokens.Inspector.padding` is 14 a side, and both sides are inside this width. */
private const val CARD_PADDING = 28
