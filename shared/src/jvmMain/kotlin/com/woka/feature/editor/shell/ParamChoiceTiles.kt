package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ParamSet
import org.jetbrains.compose.resources.painterResource

/** Tiles per row in a choice grid (`.ratio-grid`). */
private const val TILE_COLUMNS = 3

/**
 * One of more than four options, as a grid of tiles (RD-203, `#editor/crop` → Shape): the
 * option's glyph over its name, three to a row, the chosen one in the room's accent.
 *
 * It takes over from the wrapping chip row for a choice drawn as a row of its own; Markup's
 * compact bars keep the chips (`ChoiceControl`).
 */
@Composable
internal fun <E : Enum<E>> ChoiceTiles(spec: ChoiceParam<E>, params: ParamSet, writer: ParamWriter) {
    val selected = params[spec]
    Column(
        modifier = Modifier.testTag("param-${spec.key}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        spec.options.chunked(TILE_COLUMNS).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                line.forEach { option ->
                    ChoiceTile(spec, option, option == selected, Modifier.weight(1f)) { writer.edit(spec, option) }
                }
                repeat(TILE_COLUMNS - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun <E : Enum<E>> ChoiceTile(spec: ChoiceParam<E>, option: E, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val room = Theme.room
    val tint = when {
        selected -> room.accentText
        hovered -> room.text
        else -> room.muted
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .height(68.dp)
            .clip(shape)
            .background(if (selected) room.accentFill else if (hovered) room.well2 else room.well)
            .border(1.5.dp, if (selected) room.accentLine else Color.Transparent, shape)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .testTag("param-${spec.key}-${option.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        spec.iconOf(option)?.let { glyph ->
            Image(painterResource(glyph), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(tint))
        }
        BasicText(
            text = spec.labelOf(option),
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}
