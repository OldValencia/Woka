package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import kotlinx.coroutines.launch

/**
 * A long run of like sliders as **one chooser and one control** — the iOS Photos model
 * the feature doc names as this editor's UX reference (ME-161).
 *
 * **Why not a stack, and why not the compact bar.** Fifteen labelled rows measure 613 dp
 * against a window whose minimum height is 580; the panel would be the thing you cannot
 * see past, and the inspector has never scrolled. ME-155's bar wraps instead of growing,
 * but it collapses every control to a labelless button, which works because a swatch is a
 * colour and `64px` is a size — and fails here, because `+12` names nothing and an
 * adjustment is only told apart by its name. A named button is ~215 dp wide and fits one
 * per line, which is worse than the stack it replaces. All three were measured before
 * this shape was chosen.
 *
 * **This one does not grow.** Chooser plus control is about 115 dp whether the run holds
 * six adjustments or fifteen, which is what makes ME-162's six more cost nothing.
 *
 * The strip scrolls with the same chevrons and the same wheel handling as the preview
 * strip (`ParamRowStripScroll`), because a desktop wheel reports on `y` and a
 * `horizontalScroll` reads `x`.
 */
@Composable
internal fun ParamStrip(run: List<ParamSpec<*>>, params: ParamSet, writer: ParamWriter) {
    val specs = run.filterIsInstance<FloatParam>()
    // Keyed on the run's own keys: a tool whose rows come and go (Markup) would
    // otherwise keep a selection naming a spec that is no longer shown.
    var selectedKey by remember(specs.map { it.key }) { mutableStateOf(specs.first().key) }
    val selected = specs.firstOrNull { it.key == selectedKey } ?: specs.first()

    Column(verticalArrangement = Arrangement.spacedBy(STRIP_CHIP_GAP)) {
        StripChooser(specs, params, writer, selected.key) { selectedKey = it }
        ParamValueControls(selected, params, writer)
    }
}

@Composable
private fun StripChooser(
    specs: List<FloatParam>,
    params: ParamSet,
    writer: ParamWriter,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { STRIP_CHIP_STEP.toPx() }

    Row(
        modifier = Modifier.testTag("param-chooser"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StripScrollButton(back = true, enabled = scroll.canScrollBackward) {
            scope.launch { scroll.animateScrollBy(-step) }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scroll)
                // Dragged with the primary button, like a swipe (asked for by hand
                // 2026-09-01). `horizontalScroll` alone answers the wheel and nothing
                // else on desktop: `scrollable` reads drags as touch, and a mouse held
                // down and moved across the strip moved nothing at all. `draggable`
                // waits for the touch slop before it claims the gesture, which is what
                // lets a chip's own `clickable` still take a press that does not travel
                // — a drag must not select, and a click must still select.
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> scroll.dispatchRawDelta(-delta) }
                )
                .wheelScrollsStrip(scroll, scope, step),
            horizontalArrangement = Arrangement.spacedBy(STRIP_CHIP_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            specs.forEach { spec ->
                key(spec.key) {
                    StripChip(
                        spec = spec,
                        value = params[spec],
                        selected = spec.key == selectedKey,
                        onReset = { writer.resetTo(spec) },
                        onClick = { onSelect(spec.key) }
                    )
                }
            }
        }
        StripScrollButton(back = false, enabled = scroll.canScrollForward) {
            scope.launch { scroll.animateScrollBy(step) }
        }
    }
}

/** Air between two chips, and between the chooser and the control under it. */
internal val STRIP_CHIP_GAP = 6.dp

/** One press of a chevron travels about a chip and a half, as the preview strip does. */
private val STRIP_CHIP_STEP = 140.dp
