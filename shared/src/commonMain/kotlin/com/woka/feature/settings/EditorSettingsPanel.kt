package com.woka.feature.settings

import androidx.compose.runtime.Composable

/**
 * Settings → Editor: one switch per declared tool, writing
 * `WokaSettings.editorDisabledTools` (§3.4).
 *
 * `expect`/`actual` for the same reason `NativeMediaEditor` is, and it is the one
 * thing about this panel that is not obvious: this package is `commonMain` and
 * `EditorToolRegistry` is `jvmMain`, so [SettingsOverlayContent] cannot name the
 * list it draws. The seam is here rather than the registry being dragged into
 * common code to satisfy a settings card.
 *
 * No parameters — it resolves the repository itself, the newer of the two
 * conventions in this package and the one [VideoPlayerSettingsPanel] follows. The
 * older one, value + repository, is [PipSettingsPanel]; it does not fit here
 * because the tool list has no counterpart in `WokaSettings` for
 * `SettingsOverlayContent` to have collected already.
 *
 * **No per-tool code, now or ever** (§3.4). The panel iterates the registry and
 * reads `ToolManifest`, so a tool earns a switch by existing; a tool with an option
 * of its own declares a `ParamSpec`, not a row here.
 */
@Composable
expect fun EditorSettingsPanel()
