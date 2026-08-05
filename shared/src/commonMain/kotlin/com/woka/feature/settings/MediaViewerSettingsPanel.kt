package com.woka.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.CarouselVisibility
import com.woka.core.data.ScalePreset
import com.woka.core.ui.theme.Theme
import com.woka.core.ui.components.SegmentedControl

/**
 * Media-viewer settings: carousel visibility and the size state a file opens at.
 * Split out of `SettingsPanels.kt` per the §2 file-size mandate.
 */
@Composable
fun MediaViewerSettingsPanel(
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
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionLabel("Carousel")
        Spacer(modifier = Modifier.height(10.dp))
        SegmentedControl(
            options = listOf("Auto Hide", "Always On", "Hidden"),
            selected = when (carouselMode) {
                CarouselVisibility.AUTO -> "Auto Hide"
                CarouselVisibility.ALWAYS_ON -> "Always On"
                CarouselVisibility.HIDDEN -> "Hidden"
            },
            onSelect = { selectedStr ->
                onCarouselModeChange(when (selectedStr) {
                    "Always On" -> CarouselVisibility.ALWAYS_ON
                    "Hidden" -> CarouselVisibility.HIDDEN
                    else -> CarouselVisibility.AUTO
                })
            }
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("Default zoom & size")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = "Remember zoom & size",
            subtitle = "Restore the last zoom/preset you used; otherwise open at the default below",
            checked = rememberSizeState,
            onCheckedChange = onRememberSizeStateChange
        )
        Spacer(modifier = Modifier.height(12.dp))
        SegmentedControl(
            options = listOf("Preset", "Zoom"),
            selected = if (defaultUseZoom) "Zoom" else "Preset",
            onSelect = { onDefaultUseZoomChange(it == "Zoom") }
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (defaultUseZoom) {
            BasicText(
                text = "Percent of actual size — 100% is native 1:1",
                style = TextStyle(color = Theme.colors.textSecondary, fontSize = 11.sp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            ZoomDefaultStepper(defaultZoomPercent, onDefaultZoomPercentChange)
        } else {
            SegmentedControl(
                options = listOf("Fit", "Fill", "Actual"),
                selected = when (defaultPreset) {
                    ScalePreset.Fit -> "Fit"
                    ScalePreset.Fill -> "Fill"
                    ScalePreset.Original -> "Actual"
                },
                onSelect = { sel ->
                    onDefaultPresetChange(when (sel) {
                        "Fill" -> ScalePreset.Fill
                        "Actual" -> ScalePreset.Original
                        else -> ScalePreset.Fit
                    })
                }
            )
        }
    }
}

@Composable
private fun ZoomDefaultStepper(percent: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Theme.colors.surfacePressed),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperGlyph("−") { onChange((percent - 10).coerceAtLeast(10)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BasicText(
                text = "$percent%",
                style = TextStyle(color = Theme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
        }
        StepperGlyph("+") { onChange((percent + 10).coerceAtMost(1600)) }
    }
}

@Composable
private fun StepperGlyph(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = symbol,
            style = TextStyle(color = Theme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        )
    }
}
