package com.woka.feature.editor.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.PreviewChoiceParam
import org.jetbrains.compose.resources.painterResource

/**
 * One style (`.style-tile`): the picture through that option, its name under it, a ring when
 * chosen. **Hovering previews it on the canvas** — written unsettled and cancelled on the way
 * out, so looking costs no history; a click settles it.
 *
 * The 4 dp around the square is always there, so the ring arriving moves nothing.
 */
@Composable
internal fun <E : Enum<E>> StyleTile(
    spec: PreviewChoiceParam<E>,
    option: E,
    params: ParamSet,
    writer: ParamWriter,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val selected = params[spec] == option
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var previewing by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        if (hovered && !selected) {
            writer.edit(spec, option, settled = false)
            previewing = true
        } else if (previewing) {
            writer.cancel()
            previewing = false
        }
    }
    val zoom by animateFloatAsState(if (hovered) 1.07f else 1f)
    Column(
        modifier = modifier
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable {
                previewing = false
                writer.edit(spec, option)
            }
            .testTag("param-${spec.key}-${option.name}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, if (selected) Theme.room.accent else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            content(
                Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                }
            )
        }
        BasicText(
            text = spec.labelOf(option),
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = if (selected || hovered) Theme.room.text else Theme.room.muted,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}

/** ‹ or › beside a row of tiles (`.pager-arrow`); faint and still while there is no further page. */
@Composable
internal fun PagerArrow(back: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val room = Theme.room
    Box(
        modifier = Modifier
            .width(20.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (enabled && hovered) room.hoverStrong else Color.Transparent)
            .hoverable(hover)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(if (back) Icons.Static.ChevronLeft else Icons.Static.ChevronRightMac),
            contentDescription = if (back) "Previous" else "Next",
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(
                when {
                    !enabled -> room.faint
                    hovered -> room.text
                    else -> room.muted
                }
            )
        )
    }
}
