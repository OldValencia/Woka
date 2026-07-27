package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import com.woka.data.CarouselVisibility
import com.woka.data.SettingsRepository

@Composable
fun MediaViewerLayoutWrapper(
    isVisible: Boolean,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetReset: () -> Unit,
    carouselMode: CarouselVisibility,
    settingsRepo: SettingsRepository,
    currentId: String,
    onClose: (String) -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    copyTrigger: Long,
    onInfo: () -> Unit,
    isInfoOpen: Boolean,
    playerContent: @Composable () -> Unit
) {
    MediaViewerTopBarSetup(
        scale = scale,
        onScaleChange = onScaleChange,
        onOffsetReset = onOffsetReset,
        carouselMode = carouselMode,
        settingsRepo = settingsRepo,
        currentId = currentId,
        onClose = onClose,
        onEdit = onEdit,
        onCopy = onCopy,
        copyTrigger = copyTrigger,
        onInfo = onInfo,
        isInfoOpen = isInfoOpen
    )
    playerContent()
}
