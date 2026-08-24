package com.woka.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The card's name and metadata, drawn **on** the cover rather than under it.
 *
 * It replaces three things at once: the title row beneath the tile, the "Folder"
 * subtitle that said nothing on a screen made of folders, and the count badge that
 * fought the picture from the opposite corner. One zone, bottom-left, over a scrim.
 *
 * The scrim stops at [SCRIM_FRACTION] of the cover — dense at the very bottom, gone
 * by the middle — so the photograph is still a photograph. Hover does not touch it:
 * a scrim that moved would make the text swim.
 */
private const val SCRIM_FRACTION = 0.52f

private val SCRIM = Brush.verticalGradient(
    0f to Color.Transparent,
    0.34f to Color.Black.copy(alpha = 0.22f),
    0.72f to Color.Black.copy(alpha = 0.62f),
    1f to Color.Black.copy(alpha = 0.86f)
)

// Belt and braces for a cover that is nearly white where the gradient is nearly
// gone — the scrim alone cannot be trusted over an overexposed sky.
private val TEXT_SHADOW = Shadow(
    color = Color.Black.copy(alpha = 0.5f),
    offset = Offset(0f, 1f),
    blurRadius = 3f
)

@Composable
internal fun FolderCardCaption(
    name: String,
    meta: String,
    isHovered: Boolean,
    modifier: Modifier = Modifier
) {
    val metaColor by animateColorAsState(
        targetValue = Color.White.copy(alpha = if (isHovered) 0.82f else 0.62f),
        animationSpec = tween(150)
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(SCRIM_FRACTION)
                .background(SCRIM)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 9.dp)
        ) {
            BasicText(
                text = middleEllipsis(name),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    shadow = TEXT_SHADOW
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = meta,
                style = TextStyle(
                    color = metaColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = TEXT_SHADOW
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
