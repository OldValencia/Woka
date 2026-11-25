package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import com.woka.core.ui.components.SegmentedControlColors
import com.woka.core.ui.components.SwitchColors
import com.woka.core.ui.theme.Theme

/**
 * The shared components' colours for the editor's room (RD-201, `editor.css` `.segmented`,
 * `.toggle`): the inspector stands on its own panel now, not on a scrim over the picture, so
 * the `onScrim` presets would put near-white controls on the light room.
 *
 * Here rather than beside `SegmentedControlDefaults` in `core`: the room is the editor's.
 */
internal object RoomControls {

    val segmented: SegmentedControlColors
        @Composable get() = SegmentedControlColors(
            track = Theme.room.track,
            indicator = Theme.room.knob,
            indicatorBorder = Theme.room.line,
            selectedText = Theme.room.text,
            text = Theme.room.muted
        )

    /** On is the room's orange (`.toggle input:checked`), not the app's system green. */
    val switch: SwitchColors
        @Composable get() = SwitchColors(
            on = Theme.room.accent,
            off = Theme.room.switch,
            thumb = Theme.room.switchKnob
        )
}
