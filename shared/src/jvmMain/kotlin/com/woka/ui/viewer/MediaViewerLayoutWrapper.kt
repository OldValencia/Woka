package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize
import com.woka.data.CarouselVisibility
import com.woka.data.SettingsRepository

@Composable
fun MediaViewerLayoutWrapper(
    isVisible: Boolean,
    sizeMode: SizeMode,
    containerSize: IntSize,
    nativeSize: IntSize,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit,
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
        containerSize = containerSize,
        nativeSize = nativeSize,
        onSizeModeChange = onSizeModeChange,
        onOffsetReset = onOffsetReset,
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
