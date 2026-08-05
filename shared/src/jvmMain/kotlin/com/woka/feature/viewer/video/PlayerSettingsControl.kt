package com.woka.feature.viewer.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource
import com.woka.feature.viewer.shell.SimpleTooltip

/**
 * Settings ⚙ toolbar icon (§2.4), between Volume and Fullscreen. Always visible,
 * even when the file offers nothing to configure, because the menu keeps gaining
 * entries (§6). Hovering opens [PlayerSettingsOverlay] — matching the Volume and
 * Subtitle controls rather than the click in §2.4, so all three toolbar popovers
 * share one hover FSM and cannot fight each other.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerSettingsControl(
    onHoverChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    SimpleTooltip("Settings") {
        Box(
            modifier = modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .onPointerEvent(PointerEventType.Enter) { onHoverChange(true) }
                .onPointerEvent(PointerEventType.Exit) { onHoverChange(false) },
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
