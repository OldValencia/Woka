package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.ContextMenuPopup
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.shell.RoomControls
import com.woka.feature.editor.tools.trim.TrimClip
import kotlin.math.roundToInt

/** What a right press landed on: a clip, a Markup bar, or neither. */
internal sealed interface MenuTarget {
    data class Clip(val id: String) : MenuTarget
    data class Shape(val id: String) : MenuTarget
    data object Empty : MenuTarget
}

/**
 * Which menu is open and where (RD-301). [at] is written by the tracks' `Initial` pass at every
 * right press, in their own coordinates; the lane that took the press names the [target].
 */
@Stable
internal class TimelineMenuState {
    var target by mutableStateOf<MenuTarget?>(null)
        private set
    var at: Offset = Offset.Zero

    fun open(target: MenuTarget) {
        this.target = target
    }

    fun close() {
        target = null
    }
}

/**
 * A right press nothing under it took — the ruler, an empty row, the space past the lanes —
 * opens the empty-place menu. Read on the `Main` pass, which reaches this after the lanes.
 */
internal fun Modifier.emptyPlaceMenu(menu: TimelineMenuState) = pointerInput(menu) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
            val change = event.changes.first()
            if (change.isConsumed) continue
            change.consume()
            menu.open(MenuTarget.Empty)
        }
    }
}

/**
 * The timeline's context menu (`.context-menu`, RD-301) — the floating clip bar's actions and
 * the play head's ❄, in the prototype's order, with Duplicate and Join kept (the author,
 * 2026-11-26). **A right press no longer resets the cut** (2026-11-02): `Undo all cuts` in the
 * empty-place menu does, as one step.
 */
@Composable
internal fun TimelineMenu(
    menu: TimelineMenuState,
    state: StripState,
    clips: List<TrimClip>,
    edits: EditorTimelineScope,
    document: EditDocument
) {
    val target = menu.target ?: return
    ContextMenuPopup(
        offset = IntOffset(menu.at.x.roundToInt(), menu.at.y.roundToInt()),
        onDismiss = menu::close,
        colors = RoomControls.menu,
        width = TimelineTokens.menuWidth
    ) {
        val items = MenuItems(this, menu)
        when (target) {
            is MenuTarget.Clip -> ClipItems(items, target.id, state, clips, edits, document)
            is MenuTarget.Shape -> ShapeItems(items, target.id, edits, document)
            MenuTarget.Empty -> EmptyItems(items, state, clips, edits, document)
        }
    }
}

/** The menu's rows: an item closes the menu before it acts. */
internal class MenuItems(private val column: ColumnScope, private val menu: TimelineMenuState) {

    @Composable
    fun Item(label: String, enabled: Boolean = true, key: String? = null, danger: Boolean = false, onClick: () -> Unit) {
        with(column) {
            DropdownItem(
                text = label,
                enabled = enabled,
                textColor = if (danger && enabled) Theme.room.danger else null,
                trailing = key?.let { { BasicText(it, style = TimelineTokens.badge.copy(color = Theme.room.faint)) } },
                colors = RoomControls.menu
            ) {
                menu.close()
                onClick()
            }
        }
    }

    /** `.context-menu hr`. */
    @Composable
    fun Separator() {
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).height(1.dp).background(Theme.room.line))
    }
}
