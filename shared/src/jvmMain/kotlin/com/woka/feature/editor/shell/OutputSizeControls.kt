package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.SegmentedControl
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.resize.CUSTOM_KEY

/**
 * The size group both photo dialogs and a film's copy share (RD-401, `.output-size-group`):
 * the preset row, `Width` and `Height` always in view, `Keep proportions`, and the
 * renderer's refusal under them. What it all means is [OutputSizeState]'s.
 */
@Composable
internal fun OutputSizeControls(state: OutputSizeState, modifier: Modifier = Modifier) {
    val tokens = EditorDialogTokens.Group
    val room = Theme.room
    val shape = RoundedCornerShape(tokens.radius)
    val labels = remember(state.presets) { state.presets.map { it.label } + "Custom" }
    val keys = remember(state.presets) { state.presets.map { it.key } + CUSTOM_KEY }
    val widthFocus = remember { FocusRequester() }
    LaunchedEffect(state.focusWidth) {
        if (state.focusWidth > 0) runCatching { widthFocus.requestFocus() }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(room.well)
            .border(1.dp, room.line, shape)
            .padding(tokens.padding)
            // The chosen button's key, for anything reading the group rather than its pixels.
            .semantics { stateDescription = state.selected }
            .testTag("output-size")
    ) {
        BasicText(
            "Size",
            Modifier.padding(start = 5.dp, bottom = 6.dp),
            style = TextStyle(color = room.text, fontSize = tokens.legendSize, fontWeight = FontWeight.SemiBold)
        )
        SegmentedControl(
            options = labels,
            selected = labels[keys.indexOf(state.selected).coerceAtLeast(0)],
            onSelect = { label -> state.pick(keys[labels.indexOf(label)]) },
            colors = RoomControls.segmented,
            height = tokens.segmentHeight + 6.dp,
            textSize = tokens.labelSize
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = tokens.segmentGap),
            horizontalArrangement = Arrangement.spacedBy(tokens.fieldGap)
        ) {
            OutputSizeField("Width (px)", state.width, "output-width", Modifier.weight(1f), widthFocus) { state.type(0, it) }
            OutputSizeField("Height (px)", state.height, "output-height", Modifier.weight(1f)) { state.type(1, it) }
        }
        OutputSizeLock(state.keepProportions, state::lock)
        if (!state.valid) {
            BasicText(
                state.limits.error,
                Modifier.padding(top = 6.dp).testTag("output-size-error"),
                style = TextStyle(color = room.danger, fontSize = tokens.labelSize)
            )
        }
    }
}
