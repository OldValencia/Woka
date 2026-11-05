package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * The play head's two actions **either side of its line**, ✂ left and ❄ right, centred in the
 * 46 dp of film above the tabs — not on the line, which stays free to be grabbed (reported
 * 2026-11-04: stacked on it they covered it, and the play head could not be dragged).
 */
private val ACTION = 24.dp
private val ACTION_GLYPH = 14.dp
private val ACTION_GAP = 4.dp
private val ACTION_TOP = 11.dp

/**
 * Dark on the editor's orange, as `DoneButton`'s label is: white on `#FF9F0A` measures
 * 2.06 : 1 and near-black 8.28 (ME-102). White here was what made the glyphs unreadable.
 */
private val ON_ORANGE = Color(0xFF1C1C1E)

@Composable
internal fun BoxScope.PlayHeadAction(
    x: () -> Float,
    viewPx: Float,
    /** 0 is ✂, 1 is ❄ — left and right of the line, or both on the side with room. */
    slot: Int,
    icon: DrawableResource,
    label: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ViewerTooltip(
        label,
        Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(actionLeft(x(), viewPx, slot).roundToInt(), ACTION_TOP.roundToPx()) }
    ) {
        Box(
            modifier = Modifier
                .size(ACTION)
                .clip(CircleShape)
                .background(if (enabled) Theme.colors.accentEditor else Theme.viewer.onScrimDisabled)
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier)
                .testTag(tag),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(ACTION_GLYPH),
                colorFilter = ColorFilter.tint(ON_ORANGE)
            )
        }
    }
}

/** Where action [slot] sits, left of or right of the line at [head] — both on the side with room near an end. */
private fun Density.actionLeft(head: Float, viewPx: Float, slot: Int): Float {
    val side = PLAY_HEAD.toPx() / 2f + ACTION_GAP.toPx()
    val size = ACTION.toPx()
    val step = size + ACTION_GAP.toPx()
    return when {
        viewPx - head < side + size -> head - side - size - (1 - slot) * step
        head < side + size -> head + side + slot * step
        else -> if (slot == 0) head - side - size else head + side
    }
}

/**
 * Whether [at] is on one of the play head's two actions — the strip starts no gesture there.
 * Measured 2026-11-04: the strip took the press as a grab of the play head, moved it, and the
 * buttons riding it moved out from under the pointer before the click could land.
 */
internal fun Density.onPlayHeadAction(at: Offset, head: Float, viewPx: Float): Boolean {
    val top = ACTION_TOP.toPx()
    if (at.y !in top..top + ACTION.toPx()) return false
    return (0..1).any { slot -> actionLeft(head, viewPx, slot).let { at.x in it..it + ACTION.toPx() } }
}

