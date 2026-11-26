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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.SliderSwitch
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A well's plate (RD-202, `.well`): one rounded surface on the panel, a hairline between rows.
 * Its own composable since RD-302, so a panel the engine draws — Clip's Speed over Mute sound —
 * stands on the same plate as the schema's rows.
 */
@Composable
internal fun Well(rows: List<@Composable () -> Unit>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.room.well)
            .testTag("param-well")
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.room.line))
            row()
        }
    }
}

/**
 * A switch as a well's row (`.inspector .toggle`, RD-302): glyph, name, the room's switch — and
 * the whole row flips it, as a scrub row is wholly its slider.
 */
@Composable
internal fun WellToggle(label: String, icon: DrawableResource?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val room = Theme.room
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SCRUB_HEIGHT)
            .background(if (hovered) room.hover else Color.Transparent)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon?.let { glyph ->
            Image(painterResource(glyph), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(room.muted))
        }
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = room.text, fontSize = 13.sp)
        )
        SliderSwitch(checked = checked, onCheckedChange = onChange, colors = RoomControls.switch, modifier = Modifier.testTag("$tag-switch"))
    }
}
