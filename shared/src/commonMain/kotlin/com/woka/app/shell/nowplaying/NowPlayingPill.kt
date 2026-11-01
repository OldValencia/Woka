package com.woka.app.shell.nowplaying

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/**
 * `.pill`: the bubble's body (AP-006). At rest a ringed cover and nothing else; on
 * hover the capsule opens away from the window's edge — rightwards from a left
 * corner ([toRight]), leftwards from a right one — and the cover stays at the edge.
 *
 * Glass drawn here rather than through `floatingPanel()`: that modifier fixes a 20 dp
 * radius and 6 dp of padding, and the bubble is a 48 dp circle that grows sideways.
 */
@Composable
internal fun NowPlayingPill(source: NowPlayingSource, toRight: Boolean, modifier: Modifier) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(NowPlayingTokens.BubbleSize / 2)
    Row(
        modifier = modifier
            .height(NowPlayingTokens.BubbleSize)
            .shadow(12.dp, shape)
            .background(Theme.colors.surface.copy(alpha = 0.85f), shape)
            .border(1.dp, Theme.colors.border, shape)
            .hoverable(hover),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (toRight) {
            Disc(source, hovered)
            Capsule(source, hovered, toRight)
        } else {
            Capsule(source, hovered, toRight)
            Disc(source, hovered)
        }
    }
}

/** The ring, the round cover inside it, and — at rest — the state glyph over the cover. */
@Composable
private fun Disc(source: NowPlayingSource, hovered: Boolean) {
    val glyphAlpha by animateFloatAsState(if (hovered) 0f else 1f, tween(150))
    Box(
        modifier = Modifier
            .padding(1.dp)
            .size(NowPlayingTokens.RingBox)
            .clickable(remember { MutableInteractionSource() }, null, onClick = source::reveal),
        contentAlignment = Alignment.Center
    ) {
        ProgressRing(source.progress, Modifier.fillMaxSize())
        NowPlayingCover(source, NowPlayingTokens.BubbleCover, CircleShape)
        Box(
            modifier = Modifier
                .size(NowPlayingTokens.BubbleCover)
                .clip(CircleShape)
                .alpha(glyphAlpha)
                .background(Color.Black.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            if (source.isPlaying) {
                PlayingBars(Color.White)
            } else {
                Image(
                    painter = painterResource(Icons.Static.Pause),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }
        }
    }
}
