package com.woka.feature.viewer.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.app.AppDependencies
import com.woka.core.data.SubtitleFont
import com.woka.core.data.SubtitleSize
import com.woka.core.ui.icons.Icons
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

/**
 * Inline subtitle-appearance customizer (§3), shown in place of the track list
 * when "Customize Subtitles" is tapped. Reads/writes the global settings so
 * every change applies live and persists.
 */
@Composable
fun SubtitleCustomizePanel(onBack: () -> Unit) {
    val repo = remember { AppDependencies.settingsRepository }
    val settings by repo.settings.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Icons.Static.ChevronLeft),
                    contentDescription = "Back",
                    modifier = Modifier.size(13.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
            BasicText(
                text = "Customize",
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        SubtitleFieldLabel("Size")
        SubtitlePillGroup(
            options = listOf(
                "Auto" to SubtitleSize.Auto,
                "S" to SubtitleSize.Small,
                "M" to SubtitleSize.Medium,
                "L" to SubtitleSize.Large,
                "XL" to SubtitleSize.ExtraLarge
            ),
            selected = settings.subtitleSize,
            onSelect = { repo.setSubtitleSize(it) }
        )

        SubtitleFieldLabel("Font")
        SubtitlePillGroup(
            options = listOf(
                "System" to SubtitleFont.System,
                "Serif" to SubtitleFont.Serif,
                "Mono" to SubtitleFont.Monospace
            ),
            selected = settings.subtitleFont,
            onSelect = { repo.setSubtitleFont(it) }
        )

        SubtitleFieldLabel("Color")
        SubtitleColorSwatchRow(
            selectedArgb = settings.subtitleColorArgb,
            onSelect = { repo.setSubtitleColor(it) }
        )

        SubtitleFieldLabel("Background")
        SubtitlePercentStepper(
            percent = (settings.subtitleBoxOpacity * 100).roundToInt(),
            onChange = { repo.setSubtitleBoxOpacity(it / 100f) }
        )

        SubtitleFieldLabel("Outline")
        SubtitlePillGroup(
            options = listOf("On" to true, "Off" to false),
            selected = settings.subtitleOutline,
            onSelect = { repo.setSubtitleOutline(it) }
        )

        SubtitleResetRow(onClick = { repo.resetSubtitleAppearance() })
    }
}
