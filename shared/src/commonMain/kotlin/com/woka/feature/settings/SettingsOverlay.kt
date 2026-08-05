package com.woka.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import com.woka.core.data.CarouselVisibility
import com.woka.core.data.ScalePreset
import com.woka.core.ui.theme.Theme

@Composable
fun SettingsOverlay(
    isOpen: Boolean,
    onClose: () -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    globalShowTitles: Boolean,
    onGlobalShowTitlesChange: (Boolean) -> Unit,
    showHiddenFiles: Boolean,
    onShowHiddenFilesChange: (Boolean) -> Unit,
    carouselMode: CarouselVisibility,
    onCarouselModeChange: (CarouselVisibility) -> Unit,
    rememberSizeState: Boolean,
    onRememberSizeStateChange: (Boolean) -> Unit,
    defaultUseZoom: Boolean,
    onDefaultUseZoomChange: (Boolean) -> Unit,
    defaultPreset: ScalePreset,
    onDefaultPresetChange: (ScalePreset) -> Unit,
    defaultZoomPercent: Int,
    onDefaultZoomPercentChange: (Int) -> Unit
) {
    // Owned here, outside the AnimatedVisibility, so closing and reopening Settings
    // returns to the category the user was last on.
    var selectedCategory by remember { mutableStateOf("Appearance") }
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .animateEnterExit(
                        enter = fadeIn(tween(240)) + scaleIn(
                            initialScale = 0.3f,
                            transformOrigin = TransformOrigin(0.65f, 1.2f),
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                        ) + slideInVertically(
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                        ) { it / 2 },
                        exit = fadeOut(tween(150)) + scaleOut(
                            targetScale = 0.3f,
                            transformOrigin = TransformOrigin(0.65f, 1.2f),
                            animationSpec = tween(150)
                        ) + slideOutVertically(
                            animationSpec = tween(150)
                        ) { it / 2 }
                    )
                    .width(560.dp)
                    .height(420.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* absorb clicks */ }
                    .shadow(
                        elevation = 48.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.45f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(Theme.colors.surfaceElevated)
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(20.dp))
                    .padding(28.dp)
            ) {
                SettingsOverlayContent(
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                    onClose = onClose,
                    currentTheme = currentTheme,
                    onThemeChange = onThemeChange,
                    globalShowTitles = globalShowTitles,
                    onGlobalShowTitlesChange = onGlobalShowTitlesChange,
                    showHiddenFiles = showHiddenFiles,
                    onShowHiddenFilesChange = onShowHiddenFilesChange,
                    carouselMode = carouselMode,
                    onCarouselModeChange = onCarouselModeChange,
                    rememberSizeState = rememberSizeState,
                    onRememberSizeStateChange = onRememberSizeStateChange,
                    defaultUseZoom = defaultUseZoom,
                    onDefaultUseZoomChange = onDefaultUseZoomChange,
                    defaultPreset = defaultPreset,
                    onDefaultPresetChange = onDefaultPresetChange,
                    defaultZoomPercent = defaultZoomPercent,
                    onDefaultZoomPercentChange = onDefaultZoomPercentChange
                )
            }
        }
    }
}
