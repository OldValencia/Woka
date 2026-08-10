package com.woka.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WokaSettings

/**
 * The ambient-fill switch, in its own file because `VideoPlayerSettingsPanel` was
 * at 134 lines and could not absorb the body (§2 size mandate).
 *
 * The same setting is offered from the player's ⚙ popover — one global flag with
 * two entry points, the way Repeat, Shuffle and Playback Speed already work (§6).
 * It is not a per-file property: it is how the user likes to watch.
 */
@Composable
fun AmbientSettingsSection(settings: WokaSettings, repo: SettingsRepository) {
    SectionLabel("Ambient fill")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsToggleRow(
        title = "Ambient fill",
        subtitle = "Fill the black bars beside a fullscreen video with a soft glow " +
            "taken from the picture",
        checked = settings.ambientFill,
        onCheckedChange = { repo.setAmbientFill(it) }
    )
}
