package io.iris.ui.viewer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.iris.data.CarouselVisibility
import io.iris.ui.theme.IrisTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MediaViewerGlobalControls(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetReset: () -> Unit,
    carouselMode: CarouselVisibility,
    onToggleCarousel: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    copyTrigger: Long,
    isInfoOpen: Boolean,
    modifier: Modifier = Modifier
) {
    var copyHighlight by remember { mutableStateOf(false) }
    LaunchedEffect(copyTrigger) {
        if (copyTrigger > 0) {
            copyHighlight = true
            delay(300.milliseconds)
            copyHighlight = false
        }
    }
    val copyTint by animateColorAsState(
        targetValue = if (copyHighlight) IrisTheme.colors.accentFolders else IrisTheme.colors.textPrimary,
        animationSpec = tween(durationMillis = 200)
    )

    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(30.dp), spotColor = Color.Black.copy(alpha = 0.1f))
            .background(IrisTheme.colors.surface, RoundedCornerShape(30.dp))
            .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(30.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZoomOutButton(scale, onScaleChange)
        ResetZoomButton(scale, onScaleChange, onOffsetReset)
        ZoomInButton(scale, onScaleChange)

        Box(modifier = Modifier.height(16.dp).width(1.dp).background(IrisTheme.colors.border))

        InfoButton(isInfoOpen, onInfo)
        CopyButton(copyTint, onCopy)
        ToggleCarouselButton(carouselMode, onToggleCarousel)

        Box(modifier = Modifier.height(16.dp).width(1.dp).background(IrisTheme.colors.border))

        EditButton(onEdit)
    }
}
