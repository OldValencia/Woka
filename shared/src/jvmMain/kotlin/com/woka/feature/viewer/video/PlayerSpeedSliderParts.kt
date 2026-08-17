package com.woka.feature.viewer.video

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.PlaybackSpeed
import com.woka.core.ui.theme.Theme

/**
 * Pieces of [PlayerSpeedSlider], split out per the §2 file-size mandate.
 */

/** Row above the track: the "Speed" label and the current multiplier. */
@Composable
internal fun PlayerSpeedHeader(speed: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "Speed",
            style = TextStyle(color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp),
            modifier = Modifier.weight(1f)
        )
        BasicText(
            text = PlaybackSpeed.label(speed),
            style = TextStyle(
                color = if (speed == PlaybackSpeed.NORMAL) Color.White.copy(alpha = 0.6f)
                else Theme.colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
