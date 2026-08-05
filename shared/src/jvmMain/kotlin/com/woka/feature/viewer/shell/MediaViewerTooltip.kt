package com.woka.feature.viewer.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.woka.core.ui.theme.Theme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.ui.unit.DpOffset

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleTooltip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .background(Theme.viewer.tooltipScrim, RoundedCornerShape(Theme.viewer.tooltipCornerRadius))
                    .padding(Theme.viewer.tooltipPadding)
            ) {
                Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        },
        delayMillis = 300,
        tooltipPlacement = TooltipPlacement.CursorPoint(
            offset = DpOffset(0.dp, (-40).dp)
        )
    ) { content() }
}
