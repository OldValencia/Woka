package com.woka.feature.viewer.shell

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.frames.EditedDot
import com.woka.core.ui.image.AppliedVideoEdits
import com.woka.core.ui.image.rememberReloadKey
import com.woka.core.ui.theme.Theme

/** The carousel's geometry (`viewer.css` `.carousel-well`, `.media-carousel`, `.carousel-item`, RD-502). */
internal object CarouselTokens {
    val wellRadius = 12.dp
    val wellPadding = 12.dp
    val wellInset = 32.dp
    /** `box-shadow: 0 4px 24px` at 4.7 %. */
    val wellShadow = 12.dp
    const val wellFill = 0.48f
    val rowHeight = 80.dp
    val gap = 8.dp
    val item = 56.dp
    val currentItem = 72.dp
    val itemRadius = 8.dp
    val ring = 2.dp
    const val restingAlpha = 0.6f
    const val motionMs = 180
}

/** Clicks closer together than this are one: a double-click must not page twice (unchanged). */
private const val DOUBLE_CLICK_MS = 300L

/**
 * One tile: the preview, the chip, the edited dot (RD-502). Split out of
 * `MediaViewerCarousel` when the badges took it past the size mandate.
 *
 * **Dimmed by opacity, not by a veil.** The 40 % black laid over every other tile darkened
 * its badges with it; at 0.6 opacity a hover lifts the whole tile, as `.carousel-item:hover`.
 */
@Composable
internal fun CarouselItem(
    item: MediaItem,
    isCurrent: Boolean,
    thumbnails: ViewerThumbnails,
    onSelect: () -> Unit
) {
    val path = item.file.absolutePath
    var thumbnail by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var chip by remember(path) { mutableStateOf<CarouselChip>(CarouselChip.None) }
    // The tile follows its file's saves (ME-135); the path alone never moves.
    val reloadKey = rememberReloadKey(path)
    LaunchedEffect(path, reloadKey) { thumbnail = thumbnails.preview(item) }
    LaunchedEffect(path, reloadKey, AppliedVideoEdits.generation) { chip = chipFor(item) }
    val edited = rememberCarriesEdit(item)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val spec = tween<Float>(CarouselTokens.motionMs)
    val size by animateDpAsState(if (isCurrent) CarouselTokens.currentItem else CarouselTokens.item, tween(CarouselTokens.motionMs))
    val alpha by animateFloatAsState(if (isCurrent || hovered) 1f else CarouselTokens.restingAlpha, spec)
    var lastClickTime by remember { mutableStateOf(0L) }
    val curSelect by rememberUpdatedState(onSelect)
    val shape = RoundedCornerShape(CarouselTokens.itemRadius)
    Box(
        modifier = Modifier
            .size(size)
            .alpha(alpha)
            .clip(shape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime > DOUBLE_CLICK_MS) {
                        lastClickTime = now
                        curSelect()
                    }
                }
            }
    ) {
        thumbnail?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } ?: Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)))
        // The ring inside the tile, over the picture (`box-shadow: 0 0 0 2px inset`).
        if (isCurrent) Box(modifier = Modifier.fillMaxSize().border(CarouselTokens.ring, Theme.colors.accent, shape))
        MediaChip(chip, Modifier.align(Alignment.BottomStart))
        if (edited) EditedDot(Modifier.align(Alignment.TopEnd))
    }
}
