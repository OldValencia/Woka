package com.woka.feature.folder

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.woka.core.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What a key in the grid can reach: the folder, its grid, and where to go. */
internal class FolderKeyContext(
    val state: FolderState,
    val focus: FolderFocus,
    val gridState: LazyGridState,
    val scope: CoroutineScope,
    val viewModel: FolderViewModel,
    val roots: List<String>,
    val path: String,
    val open: (MediaItem) -> Unit,
    val navigate: (String) -> Unit
)

/**
 * The grid's keys (SIDE-005). Answered on the grid's own focusable box, never on the
 * global slot: there Backspace and Space would also reach the rename dialog's field
 * and the search box. The decisions are `FolderMoves`'; this is the wiring.
 */
internal fun folderKey(event: KeyEvent, ctx: FolderKeyContext): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val items = ctx.state.items
    val current = items.indexOfFirst { it.file.absolutePath == ctx.focus.focused }
    return when (event.key) {
        Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
            if (items.isEmpty()) return false
            val next = if (current < 0) 0 else when (event.key) {
                Key.DirectionLeft -> stepAcross(current, right = false, items.size)
                Key.DirectionRight -> stepAcross(current, right = true, items.size)
                else -> {
                    val down = event.key == Key.DirectionDown
                    stepVertically(current, ctx.boxes(), down, items.size)
                }
            }
            if (event.isShiftPressed && current >= 0) ctx.extendSelection(current, next)
            ctx.moveTo(next)
            true
        }
        Key.Enter, Key.NumPadEnter -> {
            val item = items.getOrNull(current) ?: return false
            ctx.focus.quickLook = false
            ctx.open(item)
            true
        }
        Key.Backspace -> {
            val parent = parentWithinRoot(ctx.path, ctx.roots) ?: return false
            ctx.navigate(parent)
            true
        }
        Key.I -> {
            if (!event.isCtrlPressed) return false
            InfoPanel.open = !InfoPanel.open
            true
        }
        Key.A -> {
            if (!event.isCtrlPressed) return false
            ctx.viewModel.selectAll()
            true
        }
        Key.Delete -> {
            val selected = ctx.state.selectedPaths.toList()
            if (selected.isEmpty()) return false
            ctx.viewModel.deleteFiles(selected)
            ctx.viewModel.toggleSelectMode()
            true
        }
        Key.Escape -> {
            when {
                ctx.focus.quickLook -> ctx.focus.quickLook = false
                ctx.state.isSelectMode -> ctx.viewModel.toggleSelectMode()
                else -> return false
            }
            true
        }
        Key.Spacebar -> {
            if (ctx.focus.quickLook) {
                ctx.focus.quickLook = false
            } else {
                // The focused item first, the pointer's only when nothing is focused
                // (2026-10-17). Pointer-first previewed whatever tile the cursor
                // happened to rest on — in a dense mosaic, nearly always one other
                // than the picture just selected.
                val target = items.getOrNull(current)?.file?.absolutePath
                    ?: ctx.focus.hovered
                    ?: items.firstOrNull()?.file?.absolutePath
                    ?: return false
                ctx.focus.focused = target
                // A folder has nothing to show whole; Space only focuses it.
                ctx.focus.quickLook = items.none { it.file.absolutePath == target && it is MediaItem.Folder }
            }
            true
        }
        else -> false
    }
}

/**
 * Shift+arrow: the item left behind joins the selection if nothing is selected yet,
 * and the selection ranges from it to [to] (`selectRange` keeps the anchor).
 */
private fun FolderKeyContext.extendSelection(from: Int, to: Int) {
    val items = state.items
    if (state.selectedPaths.isEmpty()) viewModel.toggleSelection(items[from].file.absolutePath)
    viewModel.selectRange(items[to].file.absolutePath)
}

/** The items the grid has drawn, by their place in the folder's list. */
private fun FolderKeyContext.boxes(): List<ItemBox> {
    val indexOf = state.items.withIndex().associate { (i, item) -> item.file.absolutePath to i }
    return gridState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
        val index = indexOf[info.key as? String] ?: return@mapNotNull null
        ItemBox(index, info.offset.x, info.offset.y, info.size.width)
    }
}

/** Focuses [index] and brings it on screen, scrolling only as far as it has to. */
private fun FolderKeyContext.moveTo(index: Int) {
    val path = state.items[index].file.absolutePath
    focus.focused = path
    val layout = gridState.layoutInfo
    val info = layout.visibleItemsInfo.firstOrNull { it.key == path }
    scope.launch {
        when {
            info == null -> gridState.scrollToItem(state.items.gridIndexOf(index))
            info.offset.y < layout.viewportStartOffset ->
                gridState.animateScrollBy((info.offset.y - layout.viewportStartOffset).toFloat())
            info.offset.y + info.size.height > layout.viewportEndOffset -> {
                val overshoot = info.offset.y + info.size.height - layout.viewportEndOffset
                gridState.animateScrollBy(overshoot.toFloat())
            }
        }
    }
}
