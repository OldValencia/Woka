package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import com.woka.core.ui.components.SegmentedControl
import com.woka.core.ui.components.SegmentedControlDefaults
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource
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
                    options = spec.options.map(spec.labelOf),
                    selected = spec.labelOf(selected),
                    onSelect = { label ->
                        spec.options.firstOrNull { spec.labelOf(it) == label }
                            ?.let { writer.edit(spec, it) }
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

/**
 * Chips, drawn as glyphs where the spec offers one and as words otherwise (ME-144).
 *
 * **A glyph never costs the option its name** — it moves it into a tooltip. Markup's five
 * shapes spelled out wrapped the row onto two lines, and a row that reflows as the panel
 * is used is worse than one that asks for a hover (asked for 2026-08-27); a tool whose
 * words are already short says nothing and keeps them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <E : Enum<E>> ChipRow(spec: ChoiceParam<E>, selected: E, writer: ParamWriter) {
    // **One scale for the whole row, taken from the widest glyph in it.** SF Symbols hold a
    // constant stroke per weight, but each export is cropped to its own glyph — markup's
    // five canvases run from 18.9 to 30.0 units. Fitting each into the same square scales
    // them by different factors, and the arrow came out 1.6x thicker and larger than the
    // rectangle: that is what "crooked" looked like (2026-08-27). One factor restores the
    // weight they were drawn with, and lets `textformat` be wider than `arrow` because it is.
    val glyphs = spec.options.map { option -> spec.iconOf(option)?.let { painterResource(it) } }
    val unit = glyphs.filterNotNull()
        .maxOfOrNull { max(it.intrinsicSize.width, it.intrinsicSize.height) }
        ?.takeIf { it > 0f }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        spec.options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val label = spec.labelOf(option)
            val glyph = glyphs[index]
            val tint =
                if (isSelected) Theme.colors.accentEditor else Theme.viewer.onScrimSecondary
            val chip = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (isSelected) Theme.viewer.onScrimSelected else Theme.viewer.onScrimHover
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { writer.edit(spec, option) }
                .testTag("param-${spec.key}-${option.name}")

            if (glyph == null || unit == null) {
                BasicText(
                    text = label,
                    modifier = chip.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = TextStyle(
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            } else {
                // A square chip whatever the glyph's proportions, so the row reads as five
                // buttons rather than as five shapes of assorted widths.
                ViewerTooltip(label) {
                    Box(modifier = chip.size(CHIP_SIZE), contentAlignment = Alignment.Center) {
                        Image(
                            painter = glyph,
                            contentDescription = label,
                            colorFilter = ColorFilter.tint(tint),
                            modifier = Modifier.size(
                                width = CHIP_GLYPH * (glyph.intrinsicSize.width / unit),
                                height = CHIP_GLYPH * (glyph.intrinsicSize.height / unit)
                            )
                        )
                    }
                }
            }
        }
    }
}

/** The button, square. The widest glyph in the row measures [CHIP_GLYPH]; the rest scale. */
private val CHIP_SIZE = 26.dp
private val CHIP_GLYPH = 16.dp
