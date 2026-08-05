package com.woka.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.core.ui.components.IosSwitch

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
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
                style = TextStyle(color = Theme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(color = Theme.colors.textTertiary, fontSize = 11.sp)
            )
        }
        Spacer(Modifier.width(16.dp))
        IosSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
