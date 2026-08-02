package com.woka.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.AppDependencies
import com.woka.data.WokaSettings
import com.woka.ui.viewer.player.Cue
import androidx.compose.runtime.collectAsState

/**
 * Renders text and bitmap subtitle cues as a Compose overlay over the clean
 * video frame (§2.2). Text cues are styled from the persisted appearance (§3);
 * the size auto-scales to the panel height ([BoxWithConstraints.maxHeight]).
 */
@Composable
fun SubtitleOverlay(
    cue: Cue?,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 40.dp
) {
    val settings by AppDependencies.settingsRepository.settings.collectAsState()
    val animBottom by animateDpAsState(targetValue = bottomPadding, animationSpec = tween(250))
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val panelHeightDp = maxHeight.value
        AnimatedVisibility(
            visible = cue != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (cue != null) {
                val text = cue.text
                val bitmap = cue.bitmap
                when {
                    text != null -> TextSubtitleBox(text, animBottom, settings, panelHeightDp)
                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = "Subtitle",
                        modifier = Modifier.padding(bottom = animBottom)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSubtitleBox(
    text: String,
    bottomPadding: Dp,
    settings: WokaSettings,
    panelHeightDp: Float
) {
    val fontSize = subtitleFontSizeSp(settings.subtitleSize, panelHeightDp).sp
    val base = TextStyle(
        color = settings.subtitleColorArgb.toSubtitleColor(),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = settings.subtitleFont.toFontFamily(),
        textAlign = TextAlign.Center
    )
    Box(
        modifier = Modifier
            .padding(bottom = bottomPadding, start = 40.dp, end = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = settings.subtitleBoxOpacity))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Outline = a black stroke pass drawn behind the filled text.
        if (settings.subtitleOutline) {
            Text(text = text, style = base.copy(color = Color.Black, drawStyle = Stroke(width = fontSize.value * 0.16f)))
        }
        Text(text = text, style = base)
    }
}
