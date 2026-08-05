package com.woka.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/**
 * An irreversible settings action that asks first: one click arms it, the second
 * commits, and anything else cancels.
 *
 * Confirming **in place** rather than through `ConfirmationDialog`: this row lives
 * inside the Settings card, and stacking a modal on top of a modal to ask about a
 * settings toggle is heavier than the action deserves.
 */
@Composable
fun SettingsDangerRow(
    label: String,
    confirmLabel: String,
    hint: String,
    enabled: Boolean,
    onConfirm: () -> Unit
) {
    var armed by remember { mutableStateOf(false) }
    // Nothing left to forget: disarm rather than leave a live confirm button behind.
    if (!enabled && armed) armed = false

    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        if (armed) {
            BasicText(
                text = hint,
                style = TextStyle(color = Theme.colors.textSecondary, fontSize = 11.sp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DangerButton(confirmLabel, destructive = true) {
                    onConfirm()
                    armed = false
                }
                DangerButton("Cancel", destructive = false) { armed = false }
            }
        } else {
            DangerButton(label, destructive = false, enabled = enabled) { armed = true }
        }
    }
}

@Composable
private fun DangerButton(
    text: String,
    destructive: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val content = when {
        !enabled -> Theme.colors.textSecondary.copy(alpha = 0.4f)
        destructive -> Color(0xFFFF3B30)
        else -> Theme.colors.textPrimary
    }
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Theme.colors.surfacePressed)
            .border(1.dp, Theme.colors.border, RoundedCornerShape(9.dp))
            .then(
                if (enabled) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        )
    }
}
