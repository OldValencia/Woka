package com.woka.feature.viewer.shell.frames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import org.jetbrains.compose.resources.painterResource

/**
 * Marks the one frame in a strip that plays: a Live Photo's motion clip.
 *
 * The same circular play badge `MediaViewerCarousel` puts on a video item, at
 * the smaller size the strip works at — it means the same thing in both places,
 * so it should not be a second design.
 */
@Composable
internal fun MotionBadge() {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(Icons.Static.PlayFill),
            contentDescription = "Motion",
            tint = Color.White,
            modifier = Modifier.size(10.dp)
        )
    }
}
