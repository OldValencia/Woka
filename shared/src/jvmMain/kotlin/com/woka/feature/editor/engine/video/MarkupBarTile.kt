package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import kotlin.math.roundToInt

/** `.text-bar.faded`: a fade masks 14 px at its end down to 46.7 %. */
private val FADE_MASK = 14.dp
private const val FADE_FLOOR = 0.467f

/**
 * One Markup bar (`.timeline .text-bar`, RD-301): the shape's label on the room's raised well,
 * its ends masked where it fades in or out, the room's orange ring and handles while it is the
 * shape selected on the canvas. [shown] is the part on screen of the whole [span].
 */
@Composable
internal fun MarkupBarTile(bar: MarkupBar, shown: ClipSpan, span: ClipSpan, selected: Boolean) {
    val density = LocalDensity.current
    val rowTop = (TimelineTokens.markupBarHeight + TimelineTokens.markupRowGap) * bar.row
    val shape = RoundedCornerShape(CLIP_RADIUS)
    val fadesIn = bar.window.fadeInMs > 0L && shown.left == span.left
    val fadesOut = bar.window.fadeOutMs > 0L && shown.right == span.right
    Box(
        Modifier
            .offset { IntOffset(shown.left.roundToInt(), rowTop.roundToPx()) }
            .width(with(density) { shown.width.toDp() })
            .height(TimelineTokens.markupBarHeight)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (!fadesIn && !fadesOut || size.width <= 0f) return@drawWithContent
                val edge = (FADE_MASK.toPx() / size.width).coerceAtMost(0.5f)
                val low = Color.Black.copy(alpha = FADE_FLOOR)
                drawRect(
                    Brush.horizontalGradient(
                        0f to if (fadesIn) low else Color.Black,
                        edge to Color.Black,
                        1f - edge to Color.Black,
                        1f to if (fadesOut) low else Color.Black
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .clip(shape)
            .background(Theme.room.well2)
            .then(if (selected) Modifier.border(2.dp, Theme.room.accent, shape) else Modifier)
            .testTag("editor-markup-bar-${bar.id}"),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicText(
            bar.label,
            Modifier.padding(horizontal = 12.dp),
            style = TextStyle(color = Theme.room.text, fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            BarHandle(Modifier.align(Alignment.CenterStart), RoundedCornerShape(topStart = CLIP_RADIUS, bottomStart = CLIP_RADIUS))
            BarHandle(Modifier.align(Alignment.CenterEnd), RoundedCornerShape(topEnd = CLIP_RADIUS, bottomEnd = CLIP_RADIUS))
        }
    }
}

/** A clip's handle, at a bar's height. */
@Composable
private fun BarHandle(modifier: Modifier, shape: RoundedCornerShape) {
    Box(
        modifier.width(TimelineTokens.handleWidth).fillMaxHeight().background(Theme.room.accent, shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(TimelineTokens.gripLineWidth, 12.dp)
                .background(Theme.room.onAccent.copy(alpha = 0.75f), RoundedCornerShape(1.dp))
        )
    }
}
