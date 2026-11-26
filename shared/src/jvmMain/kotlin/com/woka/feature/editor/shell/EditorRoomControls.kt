package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.SegmentedControl
import com.woka.core.ui.components.SegmentedControlColors
import com.woka.core.ui.components.SwitchColors
import com.woka.core.ui.components.frames.FrameStripColors
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
            // A knob lit from above, no hairline (`.indicator`).
            indicatorBorder = Color.Transparent,
            selectedText = Theme.room.text,
            text = Theme.room.muted
        )

    /** 36 dp: 30 dp segments inside the track's 3 dp (`.inspector .segmented`). */
    val segmentedHeight = 36.dp

    /** On is the room's orange (`.toggle input:checked`), not the app's system green. */
    val switch: SwitchColors
        @Composable get() = SwitchColors(
            on = Theme.room.accent,
            off = Theme.room.switch,
            thumb = Theme.room.switchKnob
        )

    /** The frame strip on the room's panel, the open frame in the room's orange (RD-204). */
    val frameStrip: FrameStripColors
        @Composable get() = FrameStripColors(
            card = Theme.room.panel,
            line = Theme.room.line,
            tile = Theme.colors.surfaceRaised,
            selected = Theme.room.accent
        )
}

/** The room's segmented control: a knob that slides between the options (RD-202). */
@Composable
internal fun RoomSegmented(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    SegmentedControl(
        options = options,
        selected = selected,
        onSelect = onSelect,
        colors = RoomControls.segmented,
        height = RoomControls.segmentedHeight
    )
}
