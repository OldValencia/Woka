package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable

/**
 * The control that picks which section of a tool's levels is shown (ME-164, RD-202) — the
 * room's segmented control, so it slides like every other one in the inspector.
 */
@Composable
internal fun SectionControl(sections: List<String>, selected: String, onSelect: (String) -> Unit) {
    RoomSegmented(sections, selected, onSelect)
}
