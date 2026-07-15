package io.iris.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.iris.data.CarouselVisibility
import io.iris.data.SettingsRepository
import io.iris.ui.components.LocalTopBarAccessories

@Composable
fun MediaViewerTopBarSetup(
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
    isInfoOpen: Boolean
) {
    val topBarAccessories = LocalTopBarAccessories.current
    DisposableEffect(scale, carouselMode, currentId, copyTrigger, isInfoOpen) {
        topBarAccessories.backActionOverride = { onClose(currentId) }
        topBarAccessories.centerAccessory = {
            MediaViewerGlobalControls(
                scale = scale,
                onScaleChange = onScaleChange,
                onOffsetReset = onOffsetReset,
                carouselMode = carouselMode,
                onToggleCarousel = {
                    val newMode = when (carouselMode) {
                        CarouselVisibility.AUTO -> CarouselVisibility.ALWAYS_ON
                        CarouselVisibility.ALWAYS_ON -> CarouselVisibility.HIDDEN
                        CarouselVisibility.HIDDEN -> CarouselVisibility.AUTO
                    }
                    settingsRepo.setCarouselMode(newMode)
                },
                onEdit = onEdit,
                onCopy = onCopy,
                copyTrigger = copyTrigger,
                onInfo = onInfo,
                isInfoOpen = isInfoOpen
            )
        }
        onDispose {
            topBarAccessories.backActionOverride = null
            topBarAccessories.centerAccessory = null
        }
    }
}
