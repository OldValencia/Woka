package com.woka.feature.editor.shell

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

/**
 * A choice of more than four options, each a glyph (RD-203, `#editor/photo` → Markup's pens):
 * one segmented row of icons with the room's sliding knob, the option's name in a tooltip.
 */
@Composable
internal fun <E : Enum<E>> GlyphSegmented(spec: ChoiceParam<E>, params: ParamSet, writer: ParamWriter) {
    val selected = params[spec]
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(GLYPH_ROW)
            .clip(RoundedCornerShape(10.dp))
            .background(Theme.room.track)
            .padding(3.dp)
            .testTag("param-${spec.key}")
    ) {
        val each = maxWidth / spec.options.size
        val offset by animateDpAsState(each * spec.options.indexOf(selected).coerceAtLeast(0), spring(dampingRatio = 0.78f, stiffness = 420f))
        Box(
            Modifier
                .offset(x = offset)
                .width(each)
                .height(GLYPH_ROW - 6.dp)
                .shadow(2.dp, RoundedCornerShape(7.dp))
                .background(Theme.room.knob, RoundedCornerShape(7.dp))
        )
        Row {
            spec.options.forEach { option ->
                ViewerTooltip(spec.labelOf(option)) {
                    GlyphSegment(spec, option, option == selected, each) { writer.edit(spec, option) }
                }
            }
        }
    }
}

@Composable
private fun <E : Enum<E>> GlyphSegment(spec: ChoiceParam<E>, option: E, selected: Boolean, width: Dp, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .width(width)
            .height(GLYPH_ROW - 6.dp)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .testTag("param-${spec.key}-${option.name}"),
        contentAlignment = Alignment.Center
    ) {
        val glyph = spec.iconOf(option) ?: return@Box
        Image(
            painter = painterResource(glyph),
            contentDescription = spec.labelOf(option),
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(if (selected || hovered) Theme.room.text else Theme.room.muted)
        )
    }
}

/** 36 dp, the room's segmented height (`.inspector .segmented`). */
private val GLYPH_ROW = 36.dp
