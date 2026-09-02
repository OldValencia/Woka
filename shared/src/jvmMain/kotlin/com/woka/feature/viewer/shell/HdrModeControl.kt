package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.woka.app.collectField
import com.woka.core.data.HdrViewMode
import com.woka.core.data.SettingsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Resolved here rather than threaded down, the shape `EditorHost` already uses.
 *
 * The pill is reached by two routes — the viewer's own row and the editor's
 * [CanvasZoomPill] when there is no viewer beneath — and neither passes a repository
 * through. Four files of plumbing to hand the same object to one control is worse than
 * one control asking for it.
 */
private class HdrModeDependencies : KoinComponent {
    val settings: SettingsRepository by inject()
}

/**
 * How this picture's colour is read, in the pill rather than in Settings (ME-179).
 *
 * It shipped in the settings panel first and that was wrong for a reason worth keeping:
 * this is a **per-picture question asked while looking at the picture** — is this file
 * really HDR, is the tone map helping — and an answer three screens away cannot be
 * compared against anything. `Force SDR` in particular is a probe, and a probe has to sit
 * next to what it probes.
 *
 * The same control serves the viewer and the editor because ME-177 put the tone map under
 * both. There is nothing editor-specific to mirror.
 */
@Composable
fun HdrModeControl() {
    val deps = remember { HdrModeDependencies() }
    val mode by deps.settings.collectField { it.hdrViewMode }

    var menuOpen by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    val gapPx = with(LocalDensity.current) { 6.dp.roundToPx() }

    Box(modifier = Modifier.onSizeChanged { anchorHeight = it.height }) {
        HdrModeButton(
            label = labelFor(mode),
            menuOpen = menuOpen,
            onToggleMenu = { menuOpen = !menuOpen }
        )

        if (menuOpen) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, anchorHeight + gapPx),
                onDismissRequest = { menuOpen = false },
                // Focusable for the reason `SizeControl` gives: a non-focusable popup
                // lets pointer input through to the scene and makes hover blink.
                properties = PopupProperties(focusable = true)
            ) {
                HdrModeMenu(
                    selected = mode,
                    onSelect = {
                        deps.settings.setHdrViewMode(it)
                        menuOpen = false
                    }
                )
            }
        }
    }
}

/**
 * The label names the mode, prefixed so the pill says what the number beside it does not.
 *
 * It does **not** report what `Auto` resolved to for this file, which would be the more
 * useful thing to show. The control is deliberately file-agnostic — one instance serves
 * the viewer's pill and the editor's, and neither hands it a path — so claiming to have
 * read the file would be a label the code cannot back. Open the menu to see the choice;
 * flip to SDR to see whether it was doing anything.
 */
internal fun labelFor(mode: HdrViewMode): String = when (mode) {
    HdrViewMode.Auto -> "HDR · Auto"
    HdrViewMode.ForceHdr -> "HDR · On"
    HdrViewMode.ForceSdr -> "HDR · Off"
}
