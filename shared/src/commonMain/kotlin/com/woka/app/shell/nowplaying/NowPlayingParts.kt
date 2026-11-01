package com.woka.app.shell.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// The row's and the capsule's shared pieces, split from `NowPlayingRow.kt` for the
// file-size mandate when AP-008 published the cover's position (2026-11-01).

@Composable
internal fun RowText(text: String, size: TextUnit, color: Color, weight: FontWeight) {
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
