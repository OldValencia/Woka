package com.woka.core.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.PointerButton
import com.woka.core.data.WatchRecord
import kotlinx.coroutines.delay

/**
 * A grid tile for one folder or media file. This function owns interaction —
 * hover, modifier-clicks, the right-click menu and the post-navigation highlight;
 * what it draws is [FolderCardBody] and what it draws *from* is
 * [rememberFolderCardData].
 *
 * [tile] draws the photo mosaic's square instead of the card (SIDE-004): the same
 * interaction around a different body, so a tile opens, selects and offers its menu
 * exactly as a card does. [onCheck] adds the corner circle that selects on its own.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FolderCard(
    path: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    isSelected: Boolean = false,
    isHighlighted: Boolean = false,
    contextMenuContent: (@Composable (dismissMenu: () -> Unit) -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onModifierClick: ((Boolean, Boolean) -> Unit)? = null,
    // Passed in rather than collected here: a card that subscribed to settings
    // itself would recompose every tile in the folder on any settings change at
    // all. The grid reads the map once and hands each card its own row.
    watch: WatchRecord? = null,
    dimWatched: Boolean = false,
    tile: Boolean = false,
    onCheck: (() -> Unit)? = null,
    // The keyboard's item (SIDE-005): ringed in the accent, and told where the
    // pointer is so Space can preview what is under it.
    isFocused: Boolean = false,
    onHoverChange: ((Boolean) -> Unit)? = null,
    // Drawn over the cover, under the caption: the hover excerpts (SIDE-006). A slot,
    // so `core` draws what the folder feature hands it without knowing the player.
    coverOverlay: (@Composable BoxScope.(hovered: Boolean) -> Unit)? = null
) {
    val data = rememberFolderCardData(path)

    var activeHighlight by remember(isHighlighted) { mutableStateOf(isHighlighted) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            activeHighlight = true
            delay(2000.milliseconds)
            activeHighlight = false
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    LaunchedEffect(isHovered) { onHoverChange?.invoke(isHovered) }
    // A card scrolled away under a still pointer gets no exit event, so it says so
    // on its way out of composition rather than staying "hovered" forever.
    DisposableEffect(Unit) { onDispose { onHoverChange?.invoke(false) } }

    val style = rememberFolderCardStyle(
        isHovered = isHovered,
        activeHighlight = activeHighlight,
        isSelected = isSelected,
        isDir = data.isDir
    )

    var altPressed by remember { mutableStateOf(false) }
    var shiftPressed by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier = modifier
            // A tile does not lift: in a 3.dp mosaic it would overlap its neighbours.
            .graphicsLayer { translationY = if (tile) 0f else style.translateY }
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .onPointerEvent(PointerEventType.Press) { event ->
                altPressed = event.keyboardModifiers.isAltPressed
                shiftPressed = event.keyboardModifiers.isShiftPressed
                if (event.button == PointerButton.Secondary && contextMenuContent != null) {
                    val change = event.changes.first()
                    if (!change.isConsumed) {
                        contextMenuOffset = IntOffset(change.position.x.toInt() + 2, change.position.y.toInt() + 2)
                        showContextMenu = true
                        change.consume()
                    }
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) {
                if (onModifierClick != null && (altPressed || shiftPressed)) {
                    onModifierClick(altPressed, shiftPressed)
                } else {
                    onClick()
                }
            }
    ) {
        if (tile) {
            FolderCardTileBody(data, isHovered, isSelected, watch, dimWatched, coverOverlay)
        } else {
            FolderCardBody(
                data = data,
                style = style,
                showTitle = showTitle,
                isHovered = isHovered,
                onRemove = onRemove,
                watch = watch,
                dimWatched = dimWatched,
                coverOverlay = coverOverlay
            )
        }
        FolderCardMarks(tile, isFocused, isHovered, isSelected, onCheck)

        if (showContextMenu && contextMenuContent != null) {
            FolderCardContextMenu(
                offset = contextMenuOffset,
                onDismiss = { showContextMenu = false },
                content = contextMenuContent
            )
        }
    }
}
