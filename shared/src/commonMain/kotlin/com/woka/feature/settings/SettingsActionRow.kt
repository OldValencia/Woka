package com.woka.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.woka.core.ui.theme.Theme

/**
 * A title, a subtitle and a button — the action counterpart of [SettingsToggleRow].
 * [statusMessage] appears under the subtitle when set, for the outcome of the action.
 */
@Composable
internal fun SettingsActionRow(
    title: String,
    subtitle: String,
    buttonText: String,
    enabled: Boolean = true,
    statusMessage: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val btnBg = if (!enabled) {
        Theme.colors.surfaceHover
    } else if (isHovered) {
        Theme.colors.accentSoft
    } else {
        Theme.colors.surfaceElevated
    }
    val btnBorder = if (!enabled) {
        Theme.colors.border
    } else if (isHovered) {
        Theme.colors.accent
    } else {
        Theme.colors.border
    }
    val textColor = if (!enabled) {
        Theme.colors.textTertiary
    } else if (isHovered) {
        Theme.colors.accent
    } else {
        Theme.colors.textPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.colors.surfacePressed)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(
                    color = Theme.colors.textTertiary,
                    fontSize = 11.sp
                )
            )
            if (statusMessage != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = statusMessage,
                    style = TextStyle(
                        color = Theme.colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(btnBg)
                .border(1.dp, btnBorder, RoundedCornerShape(8.dp))
                .then(
                    if (enabled) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick
                            )
                    } else Modifier
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = buttonText,
                style = TextStyle(
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

