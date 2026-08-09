package com.woka.feature.folder

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.rememberWatchMarks

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FolderGrid(
    state: FolderState,
    viewModel: FolderViewModel,
    gridState: LazyGridState,
    zoom: FolderZoom,
    path: String,
    focusRequester: FocusRequester,
    overlays: FolderOverlayState,
    onNavigateToFolder: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    highlightFile: String? = null,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(highlightFile, state.items) {
        if (highlightFile != null && state.items.isNotEmpty()) {
            val index = state.items.indexOfFirst { it.file.name == highlightFile }
            if (index != -1) {
                gridState.animateScrollToItem(index)
            }
        }
    }

    // Once for the whole grid, not once per card (§10) — see [WatchMarks].
    val marks = rememberWatchMarks(viewModel.settingsRepository)

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.button == PointerButton.Secondary) {
                    val change = event.changes.first()
                    if (!change.isConsumed) {
                        overlays.openBgContextMenu(
                            IntOffset(change.position.x.toInt() + 2, change.position.y.toInt() + 2)
                        )
                        change.consume()
                    }
                }
            }
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                if (event.keyboardModifiers.isCtrlPressed) {
                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (delta < 0) {
                        zoom.zoomIn()
                    } else if (delta > 0) {
                        zoom.zoomOut()
                    }
                    event.changes.forEach { it.consume() }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                    when (event.key) {
                        Key.Equals, Key.Plus, Key.NumPadAdd -> {
                            zoom.zoomIn()
                            true
                        }

                        Key.Minus, Key.NumPadSubtract -> {
                            zoom.zoomOut()
                            true
                        }

                        else -> false
                    }
                } else false
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(zoom.columnSize),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(top = 20.dp, start = 40.dp, end = 40.dp, bottom = 120.dp)
        ) {
            items(state.items, key = { it.file.absolutePath }) { item ->
                FolderGridItem(
                    item = item,
                    state = state,
                    viewModel = viewModel,
                    marks = marks,
                    path = path,
                    overlays = overlays,
                    onNavigateToFolder = onNavigateToFolder,
                    onOpenMedia = onOpenMedia,
                    highlightFile = highlightFile
                )
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                .padding(end = 4.dp, top = 20.dp, bottom = 120.dp),
            adapter = rememberScrollbarAdapter(gridState)
        )
    }
}
