package io.iris.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

@Composable
fun rememberGridDragDropState(
    gridState: LazyGridState,
    onMove: (Int, Int) -> Unit
): GridDragDropState {
    val state = remember(gridState) {
        GridDragDropState(gridState, onMove)
    }
    return state
}

class GridDragDropState(
    private val gridState: LazyGridState,
    private val onMove: (Int, Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal val dragOffset = mutableStateOf(Offset.Zero)

    fun onDragStart(offset: Offset) {
        gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                offset.x >= item.offset.x && offset.x <= item.offset.x + item.size.width &&
                offset.y >= item.offset.y && offset.y <= item.offset.y + item.size.height
            }
            ?.also { draggingItemIndex = it.index }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        dragOffset.value = Offset.Zero
    }

    fun onDrag(offset: Offset) {
        dragOffset.value += offset
        val draggingIndex = draggingItemIndex ?: return
        val startItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingIndex } ?: return
        
        val currentCenter = Offset(
            x = startItem.offset.x + dragOffset.value.x + startItem.size.width / 2f,
            y = startItem.offset.y + dragOffset.value.y + startItem.size.height / 2f
        )
        
        val targetItem = gridState.layoutInfo.visibleItemsInfo.find { 
            it.index != draggingIndex &&
            currentCenter.x in it.offset.x.toFloat()..(it.offset.x + it.size.width).toFloat() &&
            currentCenter.y in it.offset.y.toFloat()..(it.offset.y + it.size.height).toFloat()
        }
        
        if (targetItem != null) {
            onMove(draggingIndex, targetItem.index)
            draggingItemIndex = targetItem.index
            dragOffset.value = Offset.Zero // Reset drag offset since the item jumped to the new position
        }
    }
}

fun Modifier.dragContainer(dragDropState: GridDragDropState) = pointerInput(dragDropState) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> dragDropState.onDragStart(offset) },
        onDrag = { change, dragAmount -> 
            change.consume()
            dragDropState.onDrag(dragAmount)
        },
        onDragEnd = { dragDropState.onDragInterrupted() },
        onDragCancel = { dragDropState.onDragInterrupted() }
    )
}

fun Modifier.draggableItem(
    index: Int,
    dragDropState: GridDragDropState
) = this
    .zIndex(if (dragDropState.draggingItemIndex == index) 1f else 0f)
    .graphicsLayer {
        if (dragDropState.draggingItemIndex == index) {
            translationX = dragDropState.dragOffset.value.x
            translationY = dragDropState.dragOffset.value.y
            scaleX = 1.05f
            scaleY = 1.05f
            alpha = 0.8f
        }
    }
