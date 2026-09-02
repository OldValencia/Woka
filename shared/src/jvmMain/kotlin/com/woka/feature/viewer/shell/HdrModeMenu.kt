package com.woka.feature.viewer.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.HdrViewMode
import com.woka.core.ui.theme.Theme

private val MENU_WIDTH = 208.dp

/**
 * The three modes, each with the sentence that says what it actually does.
 *
 * The captions are load-bearing: "HDR" alone reads as *send HDR to the monitor*, which
 * Woka cannot do — it draws into an sRGB window with no HDR swapchain under it. What the
 * switch overrides is how the file is **read**, and only prose can say that.
 */
@Composable
internal fun HdrModeMenu(selected: HdrViewMode, onSelect: (HdrViewMode) -> Unit) {
    Column(
        modifier = Modifier
            .width(MENU_WIDTH)
            .shadow(12.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(14.dp))
            .background(Theme.colors.surface)
            .border(1.dp, Theme.colors.border, RoundedCornerShape(14.dp))
            .padding(8.dp)
            .testTag("hdr-mode-menu"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        HdrModeRow(HdrViewMode.Auto, "Auto", "Follow the file's own tags", selected, onSelect)
        HdrModeRow(HdrViewMode.ForceHdr, "HDR", "Read as HDR even if untagged", selected, onSelect)
        HdrModeRow(HdrViewMode.ForceSdr, "SDR", "Never tone map", selected, onSelect)
    }
}

/**
 * One mode: its name, the sentence saying what it does, and the selected tint
 * `ZoomPresetRow` uses — the two menus hang off the same pill and should not look like
 * two different products.
 *
 * A caption per row rather than a check mark alone, because the names cannot carry the
 * meaning on their own. "HDR" has to say *read as HDR*, or it reads as an output setting
 * the app does not have.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HdrModeRow(
    mode: HdrViewMode,
    title: String,
    caption: String,
    selected: HdrViewMode,
    onSelect: (HdrViewMode) -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    val isSelected = mode == selected
    val tint = if (isSelected) Theme.colors.accentFolders else Theme.colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(
                when {
                    isSelected -> Theme.colors.accentFolders.copy(alpha = 0.12f)
                    hovered -> Theme.colors.textPrimary.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable { onSelect(mode) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("hdr-mode-$title"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = tint,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            )
            BasicText(
                text = caption,
                style = TextStyle(color = Theme.colors.textSecondary, fontSize = 11.sp)
            )
        }
    }
}
