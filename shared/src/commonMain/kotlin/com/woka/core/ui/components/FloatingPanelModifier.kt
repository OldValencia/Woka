package com.woka.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

@Composable
fun Modifier.floatingPanel(): Modifier = this
    .shadow(
        elevation = 24.dp,
        shape = RoundedCornerShape(20.dp),
        spotColor = Color.Black.copy(alpha = 0.5f),
        ambientColor = Color.Black.copy(alpha = 0.5f)
    )
    .background(Theme.colors.surface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
    .padding(6.dp)
