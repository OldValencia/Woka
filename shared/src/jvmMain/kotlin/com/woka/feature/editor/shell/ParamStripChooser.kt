package com.woka.feature.editor.shell

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.SegmentedControl
import com.woka.core.ui.components.SegmentedControlDefaults
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import kotlinx.coroutines.launch

/**
 * The chips of one section, and the control that picks which section (ME-161, ME-164).
 *
 * Split from `ParamRowStrip` when the sections arrived and that file passed 150. The
 * seam is the one already there: the strip owns *what is selected*, this owns *what is
 * shown*.
 */
@Composable
internal fun SectionControl(sections: List<String>, selected: String, onSelect: (String) -> Unit) {
    SegmentedControl(
        options = sections,
        selected = selected,
        onSelect = onSelect,
        // The card is drawn on a scrim over the photograph, and this palette exists for
        // exactly that surface — the editor's inspector is named in its own KDoc.
        colors = SegmentedControlDefaults.onScrim
    )
}

@Composable
internal fun StripChooser(
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

/** One press of a chevron travels about a chip and a half, as the preview strip does. */
private val STRIP_CHIP_STEP = 140.dp
