package io.iris.ui.viewer

import androidx.compose.runtime.Composable
import io.iris.data.CarouselVisibility
import io.iris.data.SettingsRepository

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
