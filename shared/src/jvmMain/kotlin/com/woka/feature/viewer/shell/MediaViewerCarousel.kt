package com.woka.feature.viewer.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaItem
import com.woka.core.ui.icons.Icons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

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
    
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(80.dp)
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
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(mediaItems, key = { it.file.absolutePath }) { item ->
            var thumbnailBitmap by remember(item.file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(item.file.absolutePath) {
                thumbnailBitmap = thumbnails.preview(item)
            }
            var lastClickTime by remember { mutableStateOf(0L) }
            val isSelected = item.file.absolutePath == currentId
            Box(
                modifier = Modifier
                    .size(if (isSelected) 72.dp else 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime > 300) {
                                lastClickTime = now
                                onSelectMedia(item.file.absolutePath)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)))
                }
                if (!isSelected) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                }
                if (item is MediaItem.Video) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(Icons.Static.PlayFill),
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
