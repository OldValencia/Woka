package com.woka.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.woka.core.data.WatchRecord
import com.woka.core.ui.theme.Theme

/**
 * A photo-mosaic tile (SIDE-004, `.tile` in `sidebar-redesign.html`): a square, no
 * caption, the picture as its own label. The name and size appear over it only
 * while hovered, from the same `FolderCardCaption` the card wears permanently.
 *
 * Selected, the picture insets to 90 % on a tinted ground rather than gaining a
 * border, as Photos does: a ring the width of a 3.dp gap would merge with its
 * neighbours' and say nothing about which tile it belongs to.
 */
@Composable
internal fun FolderCardTileBody(
    data: FolderCardData,
    isHovered: Boolean,
    isSelected: Boolean,
    watch: WatchRecord?,
    dimWatched: Boolean,
    coverOverlay: (@Composable BoxScope.(hovered: Boolean) -> Unit)? = null
) {
    val scale by animateFloatAsState(if (isSelected) 0.9f else 1f, tween(180))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // The name is the tile's label even while it is not drawn: a tile with no
            // caption still has to answer to it, for a screen reader and for a test
            // alike. Set once here and cleared below, so the hover caption does not
            // make it two nodes.
            .clearAndSetSemantics { text = AnnotatedString(data.name) }
            .clip(RoundedCornerShape(3.dp))
            .background(if (isSelected) Theme.colors.accentSoft else Theme.colors.surfaceElevated)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(if (isSelected) 6.dp else 3.dp))
        ) {
            FolderCardPreview(
                isDir = false,
                file = data.file,
                previews = emptyList(),
                modifier = Modifier.fillMaxSize(),
                isHovered = isHovered,
                watch = watch,
                dimWatched = dimWatched
            )
            coverOverlay?.invoke(this, isHovered)
            if (isHovered) FolderCardCaption(name = data.name, meta = data.meta, isHovered = true)
            FolderCardProgress(watch)
        }
    }
}

/**
 * What sits on top of a card or tile, whichever body it has: the keyboard's focus
 * ring (SIDE-005) and the check circle (SIDE-004), shown on hover and while selected.
 */
@Composable
internal fun BoxScope.FolderCardMarks(
    tile: Boolean,
    isFocused: Boolean,
    isHovered: Boolean,
    isSelected: Boolean,
    onCheck: (() -> Unit)?
) {
    if (isFocused) {
        Box(
            Modifier.matchParentSize()
                .border(2.dp, Theme.colors.accent, RoundedCornerShape(if (tile) 3.dp else 10.dp))
        )
    }
    if (onCheck != null && (isHovered || isSelected)) {
        FolderCardCheck(isSelected, onCheck, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * `.chk`: the circle in a tile's corner that selects it without opening it — the
 * visible way into select mode, where Ctrl- and Shift-click are the quick ones.
 * Drawn on hover and while selected, filled with the accent when it is.
 */
@Composable
internal fun FolderCardCheck(
    isSelected: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(if (isSelected) Theme.colors.accent else Color.Black.copy(alpha = 0.18f))
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .clickable(onClick = onCheck),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Canvas(Modifier.size(11.dp)) {
                val w = size.width
                val stroke = 2.dp.toPx()
                val corner = Offset(w * 0.42f, w * 0.76f)
                drawLine(Color.White, Offset(w * 0.18f, w * 0.52f), corner, stroke, StrokeCap.Round)
                drawLine(Color.White, corner, Offset(w * 0.84f, w * 0.3f), stroke, StrokeCap.Round)
            }
        }
    }
}
