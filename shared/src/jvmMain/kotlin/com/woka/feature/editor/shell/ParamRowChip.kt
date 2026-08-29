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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max

/**
 * One chip of a row, and the row itself — extracted out of `ParamRowChoice.kt` when a
 * second spec needed the same chip (ME-147).
 *
 * **Extracted rather than copied**, because [ParamChipRow] carries a measurement.
 * `ChoiceParam`'s row picks one option and `ToggleSetParam`'s lights several, but what a
 * chip *is* — its size, its corner, its tint, its tooltip and the glyph scaling below —
 * is the same question with one answer, and two renderers each drawing their own is how
 * that answer drifts.
 */
internal data class ParamChip(
    /** The whole test tag, spelled by the caller — the two rows key theirs differently. */
    val tag: String,
    val label: String,
    val icon: DrawableResource?,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Chips, drawn as glyphs where one is offered and as words otherwise (ME-144).
 *
 * **A glyph never costs an option its name** — it moves it into a tooltip. Markup's five
 * shapes spelled out wrapped the row onto two lines, and a row that reflows as the panel
 * is used is worse than one that asks for a hover (asked for 2026-08-27); a row whose
 * words are already short says nothing and keeps them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParamChipRow(chips: List<ParamChip>) {
    // **One scale for the whole row, taken from the widest glyph in it.** SF Symbols hold a
    // constant stroke per weight, but each export is cropped to its own glyph — markup's
    // five canvases run from 18.9 to 30.0 units. Fitting each into the same square scales
    // them by different factors, and the arrow came out 1.6x thicker and larger than the
    // rectangle: that is what "crooked" looked like (2026-08-27). One factor restores the
    // weight they were drawn with, and lets `textformat` be wider than `arrow` because it is.
    val glyphs = chips.map { chip -> chip.icon?.let { painterResource(it) } }
    val unit = glyphs.filterNotNull()
        .maxOfOrNull { max(it.intrinsicSize.width, it.intrinsicSize.height) }
        ?.takeIf { it > 0f }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEachIndexed { index, chip ->
            val tint =
                if (chip.selected) Theme.colors.accentEditor else Theme.viewer.onScrimSecondary
            val modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (chip.selected) Theme.viewer.onScrimSelected else Theme.viewer.onScrimHover
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = chip.onClick)
                .testTag(chip.tag)

            val glyph = glyphs[index]
            if (glyph == null || unit == null) {
                ChipWord(chip.label, tint, modifier)
            } else {
                ChipGlyph(chip.label, glyph, unit, tint, modifier)
            }
        }
    }
}

@Composable
private fun ChipWord(label: String, tint: Color, modifier: Modifier) {
    BasicText(
        text = label,
        modifier = modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        style = TextStyle(color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    )
}

/**
 * A square chip whatever the glyph's proportions, so a row reads as buttons rather than
 * as shapes of assorted widths. [unit] is the row's own scale, never this glyph's.
 */
@Composable
private fun ChipGlyph(
    label: String,
    glyph: Painter,
    unit: Float,
    tint: Color,
    modifier: Modifier
) {
    ViewerTooltip(label) {
        Box(modifier = modifier.size(CHIP_SIZE), contentAlignment = Alignment.Center) {
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

/** The button, square. The widest glyph in the row measures [CHIP_GLYPH]; the rest scale. */
private val CHIP_SIZE = 26.dp
private val CHIP_GLYPH = 16.dp
