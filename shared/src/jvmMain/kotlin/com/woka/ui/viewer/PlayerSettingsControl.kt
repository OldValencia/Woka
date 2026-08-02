package com.woka.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/**
 * Settings toggle icon placeholder in the toolbar (§2.4).
 * Positions between Volume and Fullscreen controls.
 */
@Composable
fun PlayerSettingsControl(modifier: Modifier = Modifier) {
    SimpleTooltip("Settings") {
        Box(
            modifier = modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Icons.Static.Settings),
                contentDescription = "Settings",
                tint = Theme.colors.textPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
