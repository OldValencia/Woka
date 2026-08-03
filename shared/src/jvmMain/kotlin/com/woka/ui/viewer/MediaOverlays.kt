package com.woka.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.woka.ui.icons.Icons
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlayPauseOverlay(trigger: Long, isPlaying: Boolean) {
    if (trigger == 0L) return
    var visible by remember(trigger) { mutableStateOf(true) }
    
    LaunchedEffect(trigger) {
        visible = true
        delay(400.milliseconds)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(tween(150, easing = LinearOutSlowInEasing), initialScale = 0.8f),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.2f),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) Icons.Static.PlayFill else Icons.Static.Pause),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer { if (!isPlaying) scaleX = 0.7f }
                )
            }
        }
    }
}

@Composable
fun SkipOverlay(trigger: Long, direction: Int) {
    if (trigger == 0L) return
    var visible by remember(trigger) { mutableStateOf(true) }
    
    LaunchedEffect(trigger) {
        visible = true
        delay(400.milliseconds)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + slideInHorizontally(animationSpec = tween(150), initialOffsetX = { if (direction == 1) 50 else -50 }),
        exit = fadeOut(tween(200)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = if (direction == 1) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 70.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (direction == 1) Icons.Static.SkipForward5 else Icons.Static.SkipBackward5),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Current zoom, shown over the media while the wheel or keyboard changes it — the
 * indicator lives in the top bar, and you are looking at the cursor, not at it.
 * Not raised by the menu, where the value is already on screen.
 */
@Composable
fun ZoomHudOverlay(trigger: Long, label: String) {
    if (trigger == 0L) return
    var visible by remember(trigger) { mutableStateOf(true) }

    LaunchedEffect(trigger) {
        visible = true
        delay(900.milliseconds)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(250)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}
