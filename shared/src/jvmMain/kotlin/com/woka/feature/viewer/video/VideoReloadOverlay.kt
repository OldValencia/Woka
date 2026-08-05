package com.woka.feature.viewer.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Spinner shown while the video grabber is swapping tracks (§2.3). Re-opening a
 * 4K HEVC stream and decoding back to the play head takes seconds, so the last
 * frame stays on screen underneath and only this card floats above it — nothing
 * blanks, and the wait reads as deliberate rather than as a freeze.
 */
@Composable
fun VideoReloadOverlay(isVisible: Boolean) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
