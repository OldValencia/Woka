package com.woka.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private class EditorSettingsDependencies : KoinComponent {
    val registry: EditorToolRegistry by inject()
}

@Composable
actual fun EditorSettingsPanel() {
    val dependencies = remember { EditorSettingsDependencies() }
    val repository = AppDependencies.settingsRepository
    val settings by repository.settings.collectAsState()
    // `all()`, never `tools(kind)`: you cannot re-enable what is not listed, which
    // is the whole reason the registry has two accessors. Read once — the declared
    // set cannot change while the app runs, only its filtering can, and the flow
    // above is what moves the switches.
    val tools = remember { dependencies.registry.all() }

    EditorToolSettings(
        tools = tools,
        disabled = settings.editorDisabledTools,
        onToolEnabled = repository::setEditorToolEnabled
    )
}

/**
 * The panel with its container resolved away — the list, the setting it reads and
 * the write it makes, and nothing else.
 *
 * Split from [EditorSettingsPanel] because a panel that reaches for Koin itself
 * cannot be composed over a throwaway repository, which is what left
 * [VideoPlayerSettingsPanel] without a UI test (`PipSettingsPanel`'s KDoc records
 * that cost). Taking the tools as a list also means the assertions are written
 * against fixture tools and do not have to change every time a real tool ships.
 *
 * Scrolled, because six tools at 60-odd dp plus the header is already taller than
 * the Video Player panel the card was sized for.
 */
@Composable
internal fun EditorToolSettings(
    tools: List<EditorTool>,
    disabled: Set<String>,
    onToolEnabled: (String, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionLabel("Tools")
        Spacer(modifier = Modifier.height(6.dp))
        // Says what a switch costs, because "off" here is stronger than it looks:
        // it also stops operations already saved into a file from being applied,
        // and the editor reports those as skipped rather than dropping them.
        PanelHint(
            "A tool switched off leaves the editor's toolbar, and any edits it " +
                "already wrote into a file stop being applied"
        )
        Spacer(modifier = Modifier.height(10.dp))
        tools.forEachIndexed { index, tool ->
            // Between the rows, not before the first: the 10.dp under the hint is
            // the gap every other panel in this package opens above its first row.
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            val manifest = tool.manifest
            SettingsToggleRow(
                title = manifest.title,
                subtitle = manifest.summary,
                // The setting is the source of truth, not a `remember` inside the
                // switch: the set is written from here and from `settings.json`,
                // and a row holding its own state disagrees with the second.
                checked = manifest.id !in disabled,
                onCheckedChange = { onToolEnabled(manifest.id, it) },
                icon = manifest.icon
            )
        }
    }
}
