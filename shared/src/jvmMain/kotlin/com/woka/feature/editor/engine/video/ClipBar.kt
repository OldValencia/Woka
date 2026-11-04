package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.woka.core.ui.theme.Theme
import kotlin.math.roundToInt

/** `.clipbar { margin-bottom: 9px }` — the air between the bar and the clip it acts on. */
private val BAR_LIFT = 9.dp

/**
 * The selected clip's actions, floating over it (ME-213; mockup state 3).
 *
 * **Anchored to the object, not to the band**, because "the actions are where the object
 * is" is the mockup's whole reason for drawing it there. A `Popup` rather than a child:
 * the band clips its children to its rounded container, and the bar has to stand above
 * that edge. It takes no focus, so the editor's keyboard stays the editor's.
 *
 * Mute and the drag hint from the mockup are not here: mute is ME-240's, and the drag is
 * the gesture itself.
 */
@Composable
internal fun ClipBar(
    canDelete: Boolean,
    anchorX: Float,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    /** Null when no neighbour continues this clip in the source — nothing to join. */
    onJoin: (() -> Unit)? = null,
    /** The clip's speed and how to change it (ME-216); null hides the chip. */
    speed: Float = 1f,
    onSpeed: ((Float) -> Unit)? = null
) {
    val lift = with(LocalDensity.current) { BAR_LIFT.roundToPx() }
    val position = remember(anchorX, lift) { AboveAnchor(anchorX.roundToInt(), lift) }
    Popup(popupPositionProvider = position) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Theme.viewer.tooltipScrim)
                .border(1.dp, Theme.viewer.onScrimBorder, RoundedCornerShape(10.dp))
                .padding(3.dp)
                .testTag("editor-clip-bar"),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BarAction("Delete", Theme.colors.danger, enabled = canDelete, tag = "editor-clip-delete", onClick = onDelete)
            BarAction("Duplicate", Theme.viewer.onScrim, enabled = true, tag = "editor-clip-duplicate", onClick = onDuplicate)
            BarAction("Join", Theme.viewer.onScrim, enabled = onJoin != null, tag = "editor-clip-join", onClick = onJoin ?: {})
            onSpeed?.let { ClipSpeedChip(speed, it) }
        }
    }
}

@Composable
private fun BarAction(label: String, color: Color, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (enabled) color else Theme.viewer.onScrimDisabled,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/** Centred on [anchorX] of the strip, [lift] px above its top edge. */
private class AboveAnchor(private val anchorX: Int, private val lift: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left + anchorX - popupContentSize.width / 2
        val y = anchorBounds.top - popupContentSize.height - lift
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y)
    }
}
