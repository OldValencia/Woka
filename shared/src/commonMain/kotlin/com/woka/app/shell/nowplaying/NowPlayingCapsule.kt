package com.woka.app.shell.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme

/**
 * `.more`: the bubble's name and its two buttons, folded to zero width until hover
 * (AP-006). Split from `NowPlayingPill.kt` for the file-size mandate. Laid out mirror
 * image in a right corner, so `×` ends up at the far side from the cover either way.
 */
@Composable
internal fun Capsule(source: NowPlayingSource, hovered: Boolean, toRight: Boolean) {
    val from = if (toRight) Alignment.Start else Alignment.End
    val width = tween<IntSize>(NowPlayingTokens.CapsuleMs)
    AnimatedVisibility(
        visible = hovered,
        enter = expandHorizontally(width, expandFrom = from) + fadeIn(),
        exit = shrinkHorizontally(width, shrinkTowards = from) + fadeOut()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!toRight) Spacer(Modifier.width(8.dp))
            if (!toRight) CapsuleButtons(source, toRight)
            Column(
                modifier = Modifier
                    .width(NowPlayingTokens.CapsuleText)
                    .clickable(remember { MutableInteractionSource() }, null, onClick = source::reveal)
                    .padding(horizontal = 6.dp),
                horizontalAlignment = if (toRight) Alignment.Start else Alignment.End
            ) {
                val title = Theme.colors.textPrimary
                val subtitle = Theme.colors.textTertiary
                RowText(source.title, NowPlayingTokens.TitleSize, title, FontWeight.SemiBold)
                RowText(source.subtitle, NowPlayingTokens.SubtitleSize, subtitle, FontWeight.Normal)
            }
            if (toRight) CapsuleButtons(source, toRight)
            if (toRight) Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun CapsuleButtons(source: NowPlayingSource, toRight: Boolean) {
    val play = if (source.isPlaying) Icons.Static.Pause else Icons.Static.Play
    val label = if (source.isPlaying) "Pause" else "Play"
    val playTint = Theme.colors.textPrimary
    val closeTint = Theme.colors.textTertiary
    val playButton = @Composable {
        RoundIconButton(play, NowPlayingTokens.PlayGlyph, label, playTint, source::togglePlayPause)
    }
    val closeButton = @Composable {
        RoundIconButton(Icons.Static.Close, NowPlayingTokens.CloseGlyph, "Stop", closeTint, source::stop)
    }
    if (toRight) {
        playButton()
        closeButton()
    } else {
        closeButton()
        playButton()
    }
}
