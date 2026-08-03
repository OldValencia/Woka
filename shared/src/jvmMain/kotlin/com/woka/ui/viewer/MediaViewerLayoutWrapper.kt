package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import com.woka.data.CarouselVisibility
import com.woka.data.ScalePreset
import com.woka.data.SettingsRepository
import com.woka.data.ZoomPopoverView

@Composable
fun MediaViewerLayoutWrapper(
    isVisible: Boolean,
    sizeMode: SizeMode,
    savedPreset: ScalePreset,
    popoverView: ZoomPopoverView,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit,
    onPopoverViewChange: (ZoomPopoverView) -> Unit,
    carouselMode: CarouselVisibility,
    settingsRepo: SettingsRepository,
    currentId: String,
    onClose: (String) -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    isInfoOpen: Boolean,
    playerContent: @Composable () -> Unit
) {
    MediaViewerTopBarSetup(
        sizeMode = sizeMode,
        savedPreset = savedPreset,
        popoverView = popoverView,
        onSizeModeChange = onSizeModeChange,
        onOffsetReset = onOffsetReset,
        onPopoverViewChange = onPopoverViewChange,
        carouselMode = carouselMode,
        settingsRepo = settingsRepo,
        currentId = currentId,
        onClose = onClose,
        onEdit = onEdit,
        onInfo = onInfo,
        isInfoOpen = isInfoOpen
    )
    playerContent()
}
