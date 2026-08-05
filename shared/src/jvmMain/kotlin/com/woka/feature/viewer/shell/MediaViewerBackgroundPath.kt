package com.woka.feature.viewer.shell

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

fun Modifier.mediaViewerBackgroundClip(
    animationProgress: Float,
    hasPrev: Boolean,
    hasNext: Boolean,
    hasCarousel: Boolean,
    isVideo: Boolean
): Modifier = this.drawWithContent {
    val leftOffset = -100.dp.toPx() * animationProgress
    val rightOffset = 100.dp.toPx() * animationProgress
    val bottomOffset = 200.dp.toPx() * animationProgress
    
    val path = Path().apply {
        // Left Button
        if (hasPrev) {
            addRoundRect(
                RoundRect(
                    left = 16.dp.toPx() + leftOffset,
                    top = size.height / 2f - 24.dp.toPx(),
                    right = 16.dp.toPx() + 48.dp.toPx() + leftOffset,
                    bottom = size.height / 2f + 24.dp.toPx(),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                )
            )
        }
        
        // Right Button
        if (hasNext) {
            addRoundRect(
                RoundRect(
                    left = size.width - 16.dp.toPx() - 48.dp.toPx() + rightOffset,
                    top = size.height / 2f - 24.dp.toPx(),
                    right = size.width - 16.dp.toPx() + rightOffset,
                    bottom = size.height / 2f + 24.dp.toPx(),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                )
            )
        }
        
        // 4. Video Controls & Bottom Carousel
        val carouselHeightPx = if (hasCarousel) 80.dp.toPx() else 0f
        val columnBottomPx = size.height - 32.dp.toPx() + bottomOffset

        if (isVideo) {
            val controlsBottomPx = columnBottomPx - carouselHeightPx - 8.dp.toPx()
            val controlsTopPx = controlsBottomPx - 48.dp.toPx()
            addRoundRect(
                RoundRect(
                    left = 32.dp.toPx(),
                    top = controlsTopPx,
                    right = size.width - 32.dp.toPx(),
                    bottom = controlsBottomPx,
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
            )
        }

        if (hasCarousel) {
            addRoundRect(
                RoundRect(
                    left = 32.dp.toPx(),
                    top = columnBottomPx - carouselHeightPx,
                    right = size.width - 32.dp.toPx(),
                    bottom = columnBottomPx,
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
            )
        }
    }
    clipPath(path) {
        this@drawWithContent.drawContent()
    }
}
