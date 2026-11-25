package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.resetOnRightClick
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.FloatParam
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

/**
 * A level as one scrub row (RD-202, `.control.scrub`): glyph, name, number, reset — and the
 * whole row is the slider. Press anywhere and the value goes to where the pointer is; drag,
 * and it follows. A hairline along the bottom shows how far it is from its default, with a
 * tick at the default the value sticks to when it passes near.
 *
 * It replaces the stepper with its popover (ME-155): that frame was ~114 dp of buttons on a
 * card floating over the picture, where a full-width track had no room. The inspector is a
 * docked panel now, so the track is the row itself and nothing has to open.
 *
 * A drag is one history entry — previewed while it moves, settled on release; a press
 * without travel settles at once.
 */
@Composable
internal fun ScrubRow(level: ScrubLevel) {
    val spec = level.spec
    val value = level.value
    val changed = value != spec.detent
    val span = spec.range.endInclusive - spec.range.start
    var width by remember { mutableStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val room = Theme.room

    fun valueAt(x: Float): Float = snapToDetent(spec, spec.range.start + (x / width).coerceIn(0f, 1f) * span)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SCRUB_HEIGHT)
            .background(if (hovered || dragging) room.hover else Color.Transparent)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .resetOnRightClick { level.reset() }
            // Keyed on the spec: Markup swaps one size for another in the same place, and a
            // gesture that outlived the swap wrote the wrong key (`FloatControl`, 2026-08).
            .pointerInput(spec.key) { detectTapGestures { level.write(valueAt(it.x), settled = true) } }
            .pointerInput(spec.key) {
                val release = {
                    dragging = false
                    level.settle()
                }
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = release,
                    onDragCancel = release
                ) { change, _ -> level.write(valueAt(change.position.x), settled = false) }
            }
            .testTag("param-${spec.key}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(SCRUB_HEIGHT).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            spec.icon?.let { glyph ->
                Image(
                    painter = painterResource(glyph),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(if (changed) room.accentText else room.muted)
                )
            }
            BasicText(
                text = spec.label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = room.text, fontSize = 13.sp)
            )
            ScrubReset(visible = changed && hovered, tag = "param-${spec.key}-reset") { level.reset() }
            ScrubField(level, changed)
        }
        ScrubTrack(
            spec = spec,
            value = value,
            changed = changed,
            active = hovered || dragging,
            dragging = dragging,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

/** How close, as a share of the range, a value must come to its default to stick there. */
private const val SNAP_SHARE = 0.015f

/** The default the prototype's detent holds: a value passing near it lands exactly on it. */
internal fun snapToDetent(spec: FloatParam, value: Float): Float {
    val span = spec.range.endInclusive - spec.range.start
    return if (abs(value - spec.detent) <= span * SNAP_SHARE) spec.detent else value
}

internal val SCRUB_HEIGHT = 42.dp
