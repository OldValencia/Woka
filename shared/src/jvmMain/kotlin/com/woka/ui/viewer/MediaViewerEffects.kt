package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.IntSize
import com.woka.data.CarouselVisibility
import com.woka.data.SettingsRepository
import com.woka.ui.components.LocalTopBarAccessories

@Composable
fun MediaViewerTopBarSetup(
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
    isInfoOpen: Boolean
) {
    val topBarAccessories = LocalTopBarAccessories.current

    // The accessory is registered ONCE and reads live values via rememberUpdatedState.
    // Re-registering on every sizeMode change (as a DisposableEffect key) would tear
    // down and recreate the indicator on each zoom step, making the hover popover
    // flicker and the reset click hard to land.
    val curSizeMode by rememberUpdatedState(sizeMode)
    val curContainerSize by rememberUpdatedState(containerSize)
    val curNativeSize by rememberUpdatedState(nativeSize)
    val curCarousel by rememberUpdatedState(carouselMode)
    val curCurrentId by rememberUpdatedState(currentId)
    val curIsInfoOpen by rememberUpdatedState(isInfoOpen)
    val curOnSizeModeChange by rememberUpdatedState(onSizeModeChange)
    val curOnOffsetReset by rememberUpdatedState(onOffsetReset)
    val curOnClose by rememberUpdatedState(onClose)
    val curOnEdit by rememberUpdatedState(onEdit)
    val curOnInfo by rememberUpdatedState(onInfo)

    DisposableEffect(Unit) {
        topBarAccessories.backActionOverride = { curOnClose(curCurrentId) }
        topBarAccessories.centerAccessory = {
            MediaViewerNormalControls(
                sizeMode = curSizeMode,
                containerSize = curContainerSize,
                nativeSize = curNativeSize,
                onSizeModeChange = { curOnSizeModeChange(it) },
                onOffsetReset = { curOnOffsetReset() },
                carouselMode = curCarousel,
                onToggleCarousel = {
                    val newMode = when (curCarousel) {
                        CarouselVisibility.AUTO -> CarouselVisibility.ALWAYS_ON
                        CarouselVisibility.ALWAYS_ON -> CarouselVisibility.HIDDEN
                        CarouselVisibility.HIDDEN -> CarouselVisibility.AUTO
                    }
                    settingsRepo.setCarouselMode(newMode)
                },
                onEdit = { curOnEdit() },
                onInfo = { curOnInfo() },
                isInfoOpen = curIsInfoOpen
            )
        }
        onDispose {
            topBarAccessories.backActionOverride = null
            topBarAccessories.centerAccessory = null
        }
    }
}
