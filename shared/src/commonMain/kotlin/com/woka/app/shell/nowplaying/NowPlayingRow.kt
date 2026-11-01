package com.woka.app.shell.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme

/**
 * `.np`: the sidebar's Now Playing row, above `Link Folder…` (AP-004). Nothing at all
 * while the slot is empty. A click anywhere but the two buttons brings the source
 * into view.
 *
 * **No hover fill** — the author, 2026-11-01: the row already sits in its own block
 * between two hairlines, so a second outline around it says nothing. Hover slides `×` out.
 */
@Composable
fun NowPlayingRow() {
    val nowPlaying = remember { AppDependencies.nowPlaying }
    val source = nowPlaying.source ?: return
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
        Row(
            modifier = Modifier
                .padding(
                    horizontal = NowPlayingTokens.BlockHorizontal,
                    vertical = NowPlayingTokens.BlockVertical
                )
                .fillMaxWidth()
                .height(NowPlayingTokens.RowHeight)
                .hoverable(hover)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = hover, indication = null, onClick = source::reveal)
                .padding(horizontal = NowPlayingTokens.RowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NowPlayingTokens.RowGap)
        ) {
            val coverShape = RoundedCornerShape(NowPlayingTokens.CoverRadius)
            // Unclipped: a sidebar sliding out moves the cover left of zero, which is the signal.
            val publish = Modifier.onGloballyPositioned {
                nowPlaying.rowCover = Rect(it.positionInRoot(), it.size.toSize())
            }
            NowPlayingCover(source, NowPlayingTokens.CoverSize, coverShape, publish)
            Column(modifier = Modifier.weight(1f)) {
                val title = Theme.colors.textPrimary
                val subtitle = Theme.colors.textTertiary
                RowText(source.title, NowPlayingTokens.TitleSize, title, FontWeight.SemiBold)
                RowText(source.subtitle, NowPlayingTokens.SubtitleSize, subtitle, FontWeight.Normal)
            }
            val play = if (source.isPlaying) Icons.Static.Pause else Icons.Static.Play
            val playLabel = if (source.isPlaying) "Pause" else "Play"
            val playTint = Theme.colors.textPrimary
            val button = NowPlayingTokens.RowButtonSize
            Row(horizontalArrangement = Arrangement.spacedBy(NowPlayingTokens.RowButtonGap)) {
                RoundIconButton(play, NowPlayingTokens.PlayGlyph, playLabel, playTint, source::togglePlayPause, button)
                // Slides out on hover rather than fading in place, so at rest the text has its width.
                AnimatedVisibility(
                    visible = hovered,
                    enter = expandHorizontally(tween(150)) + fadeIn(tween(150)),
                    exit = shrinkHorizontally(tween(150)) + fadeOut(tween(150))
                ) {
                    val closeTint = Theme.colors.textTertiary
                    RoundIconButton(Icons.Static.Close, NowPlayingTokens.CloseGlyph, "Stop", closeTint, source::stop, button)
                }
            }
        }
    }
}
