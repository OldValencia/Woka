package com.woka.feature.editor.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip

/**
 * The inspector's tabs (RD-201, `editor.css` `.tools`): one per [EditorTab], equal widths, in
 * a sunken well at the top of the panel. A glyph over its name — the rail's lesson
 * (2026-08-21) that two tools sharing a placeholder glyph are two identical squares holds
 * here too.
 *
 * [unavailable] is per file, as it was on the rail: a tab whose tool cannot apply to what is
 * open stays in place, faint, and says why on hover — a tab that vanished would move every
 * tab after it. Clip is the one tab that comes and goes, with the selection (RD-302); it grows
 * in, and the knob follows the tabs as they move.
 */
@Composable
internal fun EditorTabBar(
    tabs: List<EditorTab>,
    selectedId: String?,
    unavailable: Map<String, String>,
    onSelect: (String) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .padding(EditorTokens.Panel.tabsMargin)
            .fillMaxWidth()
            .clip(RoundedCornerShape(EditorTokens.Panel.tabsRadius))
            .background(Theme.room.track)
            .padding(EditorTokens.Panel.tabsPadding)
            .testTag("editor-tabs")
    ) {
        val gap = EditorTokens.Panel.tabsGap
        val growth = tabGrowth(tabs.map { it.id })
        val index = tabs.indexOfFirst { it.id == selectedId }
        // The knob slides to the open tab (RD-202, `.indicator`), as the segments' does.
        val (start, width) = slotOf(growth, index.coerceAtLeast(0), maxWidth, gap)
        val offset by animateDpAsState(start, spring(dampingRatio = 0.78f, stiffness = 420f))
        if (index >= 0) TabIndicator(Modifier.offset(x = offset).width(width))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            tabs.forEachIndexed { at, tab ->
                val reason = unavailable[tab.canvasTool.manifest.id]
                if (reason != null) {
                    ViewerTooltip(reason) { TabButton(tab, selected = false, enabled = false, growth[at], onSelect) }
                } else {
                    TabButton(tab, selected = tab.id == selectedId, enabled = true, growth[at], onSelect)
                }
            }
        }
    }
}

/**
 * Each tab's share of the bar: 1, or rising from 0 for a tab that joined after the bar was
 * first drawn — 260 ms on the prototype's `cubic-bezier(.22, 1, .36, 1)`. A tab that left is
 * forgotten, so Clip grows in again on the next selection.
 */
@Composable
private fun tabGrowth(ids: List<String>): List<Float> {
    val known = remember { mutableSetOf<String>() }
    val first = known.isEmpty()
    val growth = ids.map { id ->
        key(id) {
            val grow = remember { Animatable(if (first || id in known) 1f else 0f) }
            LaunchedEffect(Unit) { grow.animateTo(1f, tween(GROW_MS, easing = GROW_EASING)) }
            grow.value
        }
    }
    SideEffect {
        known.retainAll(ids.toSet())
        known += ids
    }
    return growth
}

/** Where tab [index] starts and how wide it is, for the shares [growth] in [width]. */
private fun slotOf(growth: List<Float>, index: Int, width: Dp, gap: Dp): Pair<Dp, Dp> {
    val shares = growth.map { it.coerceAtLeast(MIN_GROWTH) }
    val unit = (width - gap * (shares.size - 1).coerceAtLeast(0)) / shares.sum().coerceAtLeast(MIN_GROWTH)
    val start = shares.take(index).fold(0.dp) { at, share -> at + unit * share + gap }
    return start to unit * (shares.getOrNull(index) ?: 0f)
}

private const val GROW_MS = 260

private val GROW_EASING = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
