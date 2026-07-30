package com.woka.ui.viewer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.woka.data.CarouselVisibility
import com.woka.data.ScalePreset
import com.woka.data.ZoomPopoverView
import com.woka.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MediaViewerControlsContainer(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(30.dp), spotColor = Color.Black.copy(alpha = 0.1f))
            .background(Theme.colors.surface, RoundedCornerShape(30.dp))
            .border(1.dp, Theme.colors.border, RoundedCornerShape(30.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
fun MediaViewerNormalControls(
    sizeMode: SizeMode,
    savedPreset: ScalePreset,
    popoverView: ZoomPopoverView,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit,
    onPopoverViewChange: (ZoomPopoverView) -> Unit,
    carouselMode: CarouselVisibility,
    onToggleCarousel: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    copyTrigger: Long,
    isInfoOpen: Boolean,
    modifier: Modifier = Modifier
) {
    var copyHighlight by remember { mutableStateOf(false) }
    LaunchedEffect(copyTrigger) {
        if (copyTrigger > 0) {
            copyHighlight = true
            delay(300.milliseconds)
            copyHighlight = false
        }
    }
    val copyTint by animateColorAsState(
        targetValue = if (copyHighlight) Theme.colors.accentFolders else Theme.colors.textPrimary,
        animationSpec = tween(durationMillis = 200)
    )

    MediaViewerControlsContainer(modifier) {
        SizeControl(sizeMode, savedPreset, popoverView, onSizeModeChange, onOffsetReset, onPopoverViewChange)

        Box(modifier = Modifier.height(16.dp).width(1.dp).background(Theme.colors.border))

        InfoButton(isInfoOpen, onInfo)
        CopyButton(copyTint, onCopy)
        ToggleCarouselButton(carouselMode, onToggleCarousel)

        Box(modifier = Modifier.height(16.dp).width(1.dp).background(Theme.colors.border))

        EditButton(onEdit)
    }
}

@Composable
fun MediaViewerFullscreenControls(
    sizeMode: SizeMode,
    savedPreset: ScalePreset,
    popoverView: ZoomPopoverView,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit,
    onPopoverViewChange: (ZoomPopoverView) -> Unit,
    carouselMode: CarouselVisibility,
    onToggleCarousel: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    isInfoOpen: Boolean,
    modifier: Modifier = Modifier
) {
    MediaViewerControlsContainer(modifier) {
        SizeControl(sizeMode, savedPreset, popoverView, onSizeModeChange, onOffsetReset, onPopoverViewChange)

        Box(modifier = Modifier.height(16.dp).width(1.dp).background(Theme.colors.border))

        InfoButton(isInfoOpen, onInfo)
        MagicWandButton()
        ToggleCarouselButton(carouselMode, onToggleCarousel, isFullscreen = true)

        Box(modifier = Modifier.height(16.dp).width(1.dp).background(Theme.colors.border))

        EditButton(onEdit)
    }
}
