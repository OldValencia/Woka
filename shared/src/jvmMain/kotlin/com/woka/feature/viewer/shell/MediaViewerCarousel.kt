package com.woka.feature.viewer.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaItem
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.launch

internal const val CAROUSEL_TAG = "viewer-carousel"

/**
 * The folder's files under the picture, in a well (`#viewer/photo`, `.carousel-well`, RD-502):
 * neighbours dimmed, the current one larger and ringed, each tile saying what its file is.
 *
 * The well is the background at 48 %, not `glass`, as the prototype mixes it
 * (`color-mix(background 48 %)`); its `backdrop-filter` blur is not drawn, as the
 * transport's is not.
 */
@Composable
fun MediaViewerCarousel(
    mediaItems: List<MediaItem>,
    currentId: String,
    currentIndex: Int,
    onSelectMedia: (String) -> Unit,
    thumbnails: ViewerThumbnails
) {
    if (mediaItems.size <= 1) return

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentId) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex, scrollOffset = -200)
        }
    }

    val shape = RoundedCornerShape(CarouselTokens.wellRadius)
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CarouselTokens.wellInset)
            .shadow(CarouselTokens.wellShadow, shape, clip = false, spotColor = Color.Black.copy(alpha = 0.047f))
            .clip(shape)
            .background(Theme.colors.background.copy(alpha = CarouselTokens.wellFill))
            .border(1.dp, Theme.colors.border, shape)
            .height(CarouselTokens.rowHeight)
            .testTag(CAROUSEL_TAG)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch { listState.scrollBy(-delta) }
                }
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.first().scrollDelta.y
                            if (delta != 0f) {
                                coroutineScope.launch {
                                    listState.scrollBy(delta * 50f)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            },
        contentPadding = PaddingValues(horizontal = CarouselTokens.wellPadding),
        horizontalArrangement = Arrangement.spacedBy(CarouselTokens.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(mediaItems, key = { it.file.absolutePath }) { item ->
            CarouselItem(
                item = item,
                isCurrent = item.file.absolutePath == currentId,
                thumbnails = thumbnails,
                onSelect = { onSelectMedia(item.file.absolutePath) }
            )
        }
    }
}
