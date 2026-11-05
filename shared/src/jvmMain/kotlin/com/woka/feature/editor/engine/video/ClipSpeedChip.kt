package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.CLIP_SPEEDS

/**
 * The selected clip's speed, `1× ▾`, in the clip bar (ME-216): the quick values as one click
 * each, and a slider for everything between — 1.25×, 1.5× and the rest the author asked for
 * on 2026-11-04, when five fixed speeds were too few.
 */
@Composable
internal fun ClipSpeedChip(speed: Float, onSpeed: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ChipLabel("${speedLabel(speed)} ▾", "editor-clip-speed", selected = false) { open = !open }
        if (open) {
            Popup(alignment = Alignment.BottomCenter, offset = IntOffset(0, -34), onDismissRequest = { open = false }) {
                SpeedPanel(speed, onSpeed)
            }
        }
    }
}

@Composable
private fun SpeedPanel(speed: Float, onSpeed: (Float) -> Unit) {
    // The knob follows the drag; the clip hears only the release — one history step.
    var dragging by remember { mutableStateOf<Float?>(null) }
    Column(
        Modifier.width(250.dp).clip(RoundedCornerShape(10.dp)).background(Theme.viewer.tooltipScrim).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BasicText(
            "Speed ${speedLabel(dragging ?: speed)}",
            style = TextStyle(color = Theme.viewer.onScrim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
            CLIP_SPEEDS.forEach { option ->
                ChipLabel(speedLabel(option), "editor-clip-speed-$option", selected = option == speed) { onSpeed(option) }
            }
        }
        ClipSpeedSlider(dragging ?: speed, onDrag = { dragging = it }) { picked ->
            dragging = null
            onSpeed(picked)
        }
    }
}

@Composable
internal fun ChipLabel(text: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .then(if (selected) Modifier.background(Theme.viewer.onScrim.copy(alpha = 0.16f)) else Modifier)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = Theme.viewer.onScrim, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}
