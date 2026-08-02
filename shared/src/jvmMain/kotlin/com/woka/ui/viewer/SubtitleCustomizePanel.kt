package com.woka.ui.viewer

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.AppDependencies
import com.woka.data.SubtitleFont
import com.woka.data.SubtitleSize
import com.woka.ui.icons.Icons
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

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
                Icon(
                    painter = painterResource(Icons.Static.ChevronLeft),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = "Customize",
                style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        FieldLabel("Size")
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

        FieldLabel("Font")
        SubtitlePillGroup(
            options = listOf(
                "System" to SubtitleFont.System,
                "Serif" to SubtitleFont.Serif,
                "Mono" to SubtitleFont.Monospace
            ),
            selected = settings.subtitleFont,
            onSelect = { repo.setSubtitleFont(it) }
        )

        FieldLabel("Color")
        SubtitleColorSwatchRow(
            selectedArgb = settings.subtitleColorArgb,
            onSelect = { repo.setSubtitleColor(it) }
        )

        FieldLabel("Background")
        SubtitlePercentStepper(
            percent = (settings.subtitleBoxOpacity * 100).roundToInt(),
            onChange = { repo.setSubtitleBoxOpacity(it / 100f) }
        )

        FieldLabel("Outline")
        SubtitlePillGroup(
            options = listOf("On" to true, "Off" to false),
            selected = settings.subtitleOutline,
            onSelect = { repo.setSubtitleOutline(it) }
        )

        ResetRow(onClick = { repo.resetSubtitleAppearance() })
    }
}

@Composable
private fun ResetRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(7.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Reset to defaults",
            style = TextStyle(
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = TextStyle(
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    )
}
