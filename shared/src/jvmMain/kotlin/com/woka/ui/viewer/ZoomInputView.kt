package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Display for the manual zoom value. Keyboard capture happens on the indicator in the
 * main window (a focusable popup would break the popover's hover), so this is a passive
 * field that reflects [text], with a blinking caret to signal it is being edited.
 */
@Composable
fun ZoomInputView(text: String, modifier: Modifier = Modifier) {
    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530.milliseconds)
            caretOn = !caretOn
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Theme.colors.surfaceElevated.copy(alpha = 0.6f))
            .border(1.dp, Theme.colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = text.ifEmpty { "0" },
            style = TextStyle(color = Theme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .width(1.1.dp)
                .height(15.dp)
                .background(Theme.colors.textPrimary.copy(alpha = if (caretOn) 0.9f else 0f))
        )
        Text(
            text = "%",
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}
