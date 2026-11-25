package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ParamSet
import org.jetbrains.compose.resources.painterResource

/**
 * Scrub rows grouped in a well (RD-202, `.well`): one rounded plate on the panel, a hairline
 * between rows. Every run of levels in the inspector is drawn as one — Adjust's sections,
 * Styles' intensity, a film's speed.
 */
@Composable
internal fun ScrubWell(specs: List<ParamSpec<*>>, params: ParamSet, writer: ParamWriter) {
    // A pad is two rows over one stored point — see `ScrubLevel`.
    val levels = specs.flatMap { writer.levelsOf(it, params) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.room.well)
            .testTag("param-well")
    ) {
        levels.forEachIndexed { index, level ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.room.line))
            key(level.spec.key) { ScrubRow(level) }
        }
    }
}

/**
 * An action as a full-width well button (`.well-button`, `.auto-button`): Adjust's
 * Auto-enhance. The glyph takes the room's accent, the word its text.
 */
@Composable
internal fun WellButton(spec: ActionParam, writer: ParamWriter, modifier: Modifier = Modifier, accentGlyph: Boolean = false) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Theme.room.well2 else Theme.room.well)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { writer.edit(spec, true) }
            .testTag("param-${spec.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        spec.icon?.let { glyph ->
            val tint = if (accentGlyph) Theme.room.accent else Theme.room.muted
            Image(painterResource(glyph), null, Modifier.size(17.dp), colorFilter = ColorFilter.tint(tint))
        }
        BasicText(
            spec.label,
            style = TextStyle(color = Theme.room.text, fontSize = 13.sp, fontWeight = if (accentGlyph) FontWeight.SemiBold else FontWeight.Normal)
        )
    }
}

/**
 * An action as a quiet line under the rows (`.text-action`): Adjust's Reset adjustments —
 * something done rarely and on purpose, so it does not wear a plate.
 */
@Composable
internal fun TextAction(spec: ActionParam, writer: ParamWriter) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val tint = if (hovered) Theme.room.text else Theme.room.muted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered) Theme.room.hoverStrong else Color.Transparent)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { writer.edit(spec, true) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("param-${spec.key}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        spec.icon?.let { glyph ->
            Image(painterResource(glyph), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(tint))
        }
        BasicText(spec.label, style = TextStyle(color = tint, fontSize = 12.sp))
    }
}

/** A group's name above its control (`.group-label`): Shape, Orientation, a choice's own word. */
@Composable
internal fun GroupLabel(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        style = TextStyle(color = Theme.room.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    )
}

/** Actions side by side, two to a row (`.transform-actions`). */
@Composable
internal fun ActionGrid(actions: List<ActionParam>, writer: ParamWriter) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { spec -> key(spec.key) { WellButton(spec, writer, Modifier.weight(1f)) } }
            }
        }
    }
}
