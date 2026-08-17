package com.woka.feature.viewer.shell

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

@Composable
fun PlayPauseOverlay(event: ViewerEvent, isPlaying: Boolean) {
    TransientOverlay(
        event = event,
        durationMs = 400L,
        enter = fadeIn(tween(150)) +
            scaleIn(tween(150, easing = LinearOutSlowInEasing), initialScale = 0.8f),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.2f)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Theme.viewer.hudScrim, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(if (isPlaying) Icons.Static.PlayFill else Icons.Static.Pause),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { if (!isPlaying) scaleX = 0.7f },
                colorFilter = ColorFilter.tint(Color.White)
            )
        }
    }
}

@Composable
fun SkipOverlay(event: ViewerEvent, direction: Int) {
    TransientOverlay(
        event = event,
        durationMs = 400L,
        enter = fadeIn(tween(150)) + slideInHorizontally(
            animationSpec = tween(150),
            initialOffsetX = { if (direction == 1) 50 else -50 }
        ),
        exit = fadeOut(tween(200)),
        alignment = if (direction == 1) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 70.dp)
                .size(48.dp)
                .background(Theme.viewer.hudScrim, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    if (direction == 1) Icons.Static.SkipForward5 else Icons.Static.SkipBackward5
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )
        }
    }
}

/**
 * Current zoom, shown over the media while the wheel or keyboard changes it — the
 * indicator lives in the top bar, and you are looking at the cursor, not at it.
 * Not raised by the menu, where the value is already on screen.
 */
@Composable
fun ZoomHudOverlay(event: ViewerEvent, label: String) {
    TransientOverlay(
        event = event,
        durationMs = 900L,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(250))
    ) {
        Box(
            modifier = Modifier
                .background(Theme.viewer.popoverScrim, RoundedCornerShape(Theme.viewer.cornerRadius))
                .padding(Theme.viewer.hudPadding)
        ) {
            BasicText(text = label, style = Theme.viewer.hudLabel.copy(color = Color.White))
        }
    }
}
