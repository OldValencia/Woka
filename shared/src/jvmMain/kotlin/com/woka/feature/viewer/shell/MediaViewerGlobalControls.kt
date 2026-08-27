package com.woka.feature.viewer.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.app.shell.LocalCanvasOverride
import com.woka.core.data.CarouselVisibility
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.zoom.SizeControl

@Composable
fun MediaViewerControlsContainer(
    modifier: Modifier = Modifier,
    /**
     * `spacedBy` puts a gap beside a child even when that child has measured itself to
     * nothing, so a pill with a retracting group keeps 8.dp of dead space at the edge
     * for the rest of the session. Those pills space themselves with [PillGap] instead.
     */
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(PILL_GAP),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(30.dp), spotColor = Color.Black.copy(alpha = 0.1f))
            .background(Theme.colors.surface, RoundedCornerShape(30.dp))
            .border(1.dp, Theme.colors.border, RoundedCornerShape(30.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

private val PILL_GAP = 8.dp

@Composable
private fun PillGap() {
    Spacer(modifier = Modifier.width(PILL_GAP))
}

@Composable
private fun PillDivider() {
    Box(modifier = Modifier.height(16.dp).width(1.dp).background(Theme.colors.border))
}

@Composable
fun MediaViewerNormalControls(
    sizeMode: SizeMode,
    containerSize: IntSize,
    nativeSize: IntSize,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit,
    carouselMode: CarouselVisibility,
    onToggleCarousel: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    isInfoOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val override = LocalCanvasOverride.current
    val zoom = override.zoom

    MediaViewerControlsContainer(modifier, arrangement = Arrangement.Start) {
        // The same control either way, pointed somewhere else: while the editor is up
        // it owns the picture, and the viewer underneath is frozen (see CanvasZoom).
        if (zoom != null) {
            CanvasZoomControl(zoom)
        } else {
            // The two callbacks are the same lambda here and that is not an
            // oversight: for the viewer, "put the frame back" *is* zeroing the
            // offset, and no zoom rides along with it. Only the editor's pill has
            // a reset that means more than that.
            SizeControl(
                sizeMode, containerSize, nativeSize, onSizeModeChange, onOffsetReset,
                onRecentre = onOffsetReset
            )
        }

        PillGap()
        PillDivider()
        PillGap()
        InfoButton(isInfoOpen, onInfo)

        // One group, so the pill narrows in a single movement rather than closing two
        // holes at once. Everything that goes with them — the gaps and the divider —
        // is inside, which is what lets the pill end flush against Info.
        AnimatedVisibility(visible = !override.editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillGap()
                ToggleCarouselButton(carouselMode, onToggleCarousel)
                PillGap()
                PillDivider()
                PillGap()
                EditButton(onEdit)
            }
        }
    }
}

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
