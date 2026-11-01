package com.woka.app.shell.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * `.np`: the sidebar's Now Playing row, above `Link Folder…` (AP-004). Nothing at all
 * while the slot is empty. A click anywhere but the two buttons brings the source
 * into view.
 *
 * **No hover fill** — the author, 2026-11-01: the row already sits in its own block
 * between two hairlines, so a second outline around it says nothing. Hover shows `×`.
 */
@Composable
fun NowPlayingRow() {
    val nowPlaying = remember { AppDependencies.nowPlaying }
    val source = nowPlaying.source ?: return
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val closeAlpha by animateFloatAsState(if (hovered) 1f else 0f, tween(150))
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
            NowPlayingCover(source, NowPlayingTokens.CoverSize, coverShape)
            Column(modifier = Modifier.weight(1f)) {
                val title = Theme.colors.textPrimary
                val subtitle = Theme.colors.textTertiary
                RowText(source.title, NowPlayingTokens.TitleSize, title, FontWeight.SemiBold)
                RowText(source.subtitle, NowPlayingTokens.SubtitleSize, subtitle, FontWeight.Normal)
            }
            val play = if (source.isPlaying) Icons.Static.Pause else Icons.Static.Play
            val playLabel = if (source.isPlaying) "Pause" else "Play"
            val playTint = Theme.colors.textPrimary
            RoundIconButton(play, NowPlayingTokens.PlayGlyph, playLabel, playTint, source::togglePlayPause)
            Box(modifier = Modifier.alpha(closeAlpha)) {
                val closeTint = Theme.colors.textTertiary
                RoundIconButton(Icons.Static.Close, NowPlayingTokens.CloseGlyph, "Stop", closeTint, source::stop)
            }
        }
    }
}

@Composable
private fun RowText(text: String, size: TextUnit, color: Color, weight: FontWeight) {
    BasicText(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight)
    )
}

/**
 * `.rb`: a 26 dp circle with a hover fill. Not `ToolbarIconButton` — that is the bar's
 * 30×28 rounded square with a fixed 15 dp glyph, and the mockup draws these round and
 * smaller, with a 13 dp play glyph and a 10 dp `×`.
 */
@Composable
internal fun RoundIconButton(
    icon: DrawableResource,
    glyph: Dp,
    label: String,
    rest: Color,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill by animateColorAsState(
        if (hovered) Theme.colors.surfaceHover else Color.Transparent,
        tween(150)
    )
    Box(
        modifier = Modifier
            .size(NowPlayingTokens.ButtonSize)
            .clip(CircleShape)
            .background(fill)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(glyph),
            colorFilter = ColorFilter.tint(if (hovered) Theme.colors.textPrimary else rest)
        )
    }
}
