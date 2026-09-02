package com.woka.feature.viewer.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.woka.core.ui.theme.Theme
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.SliderSwitch
import com.woka.core.ui.components.SwitchDefaults
import com.woka.core.ui.icons.Icons
import com.woka.feature.viewer.shell.SimpleTooltip
import org.jetbrains.compose.resources.painterResource

/** Where the boost switch is driven from, for the UI tests. */
internal const val SOUND_BOOST_SWITCH_TAG = "sound-boost-switch"

/**
 * "Sound boost", under the volume slider (asked for 2026-08-29).
 *
 * **In the popover rather than in player settings**, because it is read where the slider
 * is: the switch does not raise the level, it *permits* the level to go higher, and a
 * permission set two panels away would be a slider that silently stops in one session and
 * not in the next.
 *
 * `SwitchDefaults.onScrim` — the variant written for a switch drawn over media, which is
 * what this popover is.
 */
@Composable
internal fun SoundBoostRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    // **`IntrinsicSize.Max`, or the rule below eats the screen.** The popover sizes to its
    // content, so it is measured with the window's width as the maximum — and a
    // `fillMaxWidth` child takes the maximum it is offered, not the width of its siblings.
    // Without this the hairline stretched the whole popover across the picture.
    Column(modifier = Modifier.width(IntrinsicSize.Max).padding(top = 10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Theme.viewer.onScrimBorder)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            // **A glyph, not a word** (2026-08-29). "Sound boost" — and even "Boost" —
            // set the popover's width for a row that is only a switch, and this
            // popover is otherwise 16 dp of slider. The name lives in the tooltip,
            // the way the pen chips have since ME-155.
            //
            // A speaker with a bolt beside it rather than the loudest speaker: that
            // one is already the volume button two rows below, and repeating it here
            // would read as a second volume rather than as permission to pass 100 %.
            // The bolt stands at the speaker's own height — inside a badge it was too
            // small to read at 16 dp.
            //
            // Dimmed while off, so the row states which way it is set even where the
            // switch is glanced past.
            SimpleTooltip("Sound boost") {
                Image(
                    painter = painterResource(Icons.Static.SpeakerBoltFill),
                    contentDescription = "Sound boost",
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(
                        if (enabled) Theme.viewer.onScrim else Theme.viewer.onScrimDisabled
                    )
                )
            }
            SliderSwitch(
                checked = enabled,
                onCheckedChange = onChange,
                modifier = Modifier.testTag(SOUND_BOOST_SWITCH_TAG),
                colors = SwitchDefaults.onScrim
            )
        }
    }
}
