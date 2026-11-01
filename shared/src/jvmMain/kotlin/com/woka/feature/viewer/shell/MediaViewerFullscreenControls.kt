package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.CarouselVisibility
import com.woka.feature.viewer.shell.zoom.SizeControl

/**
 * The fullscreen/PiP window's row. Info and Edit are **not** here: this window has
 * no file open in the sense they mean, and both were wired to no-op lambdas —
 * buttons that looked live and did nothing. The carousel icon stays as the
 * deliberately inert indicator its own tooltip describes.
 */
@Composable
fun MediaViewerFullscreenControls(
    sizeMode: SizeMode,
    containerSize: IntSize,
    nativeSize: IntSize,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit,
    carouselMode: CarouselVisibility,
    onToggleCarousel: () -> Unit,
    isPipWindow: Boolean,
    onTogglePipWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    MediaViewerControlsContainer(modifier) {
        SizeControl(
            sizeMode, containerSize, nativeSize, onSizeModeChange, onOffsetReset,
            onRecentre = onOffsetReset
        )

        PillDivider()

        PipWindowButton(isPipWindow, onTogglePipWindow)
        ToggleCarouselButton(carouselMode, onToggleCarousel, isFullscreen = true)
    }
}
