package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import kotlin.math.roundToInt

/** The tabs' row under the clips while Trim is open. */
internal val CLIP_TAB_HEIGHT = 14.dp

private val TAB_MAX = 56.dp

/** Dark on the editor's orange, as `DoneButton`'s label is — white on it reads at 2 : 1. */
private val ON_ACCENT = Color(0xFF1C1C1E)

/**
 * One tab under each clip, to **select it without moving the play head** (the author,
 * 2026-11-04: a click on the clip both selected it and sought, which read as an accident).
 * The clip's body now only seeks — or reorders, on a drag — and its tab only selects.
 * Numbered in timeline order; a freeze says so.
 */
@Composable
internal fun BoxScope.ClipTabs(clips: List<TrimClip>, geometry: StripGeometry, selectedId: String?, onSelect: (String) -> Unit) {
    val density = LocalDensity.current
    clips.forEachIndexed { index, clip ->
        val shown = geometry.spans[index].visibleIn(geometry.viewPx) ?: return@forEachIndexed
        val widthPx = minOf(shown.width - 4f, with(density) { TAB_MAX.toPx() })
        if (widthPx < with(density) { 12.dp.toPx() }) return@forEachIndexed
        val selected = clip.id == selectedId
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset((shown.left + (shown.width - widthPx) / 2f).roundToInt(), 0) }
                .width(with(density) { widthPx.toDp() })
                .height(CLIP_TAB_HEIGHT - 2.dp)
                .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                .background(if (selected) Theme.colors.accentEditor else Theme.viewer.onScrim.copy(alpha = 0.16f))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { onSelect(clip.id) }
                .testTag("editor-clip-tab-${clip.id}"),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                if (clip.isFreeze) "Hold" else "${index + 1}",
                style = TextStyle(
                    color = if (selected) ON_ACCENT else Theme.viewer.onScrim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
