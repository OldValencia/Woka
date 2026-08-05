package com.woka.feature.viewer.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerTooltip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(6.dp))
                    .background(Theme.colors.surfaceElevated, RoundedCornerShape(6.dp))
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text,
                    style = TextStyle(
                        color = Theme.colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        },
        delayMillis = 800,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 24.dp))
    ) {
        content()
    }
}
