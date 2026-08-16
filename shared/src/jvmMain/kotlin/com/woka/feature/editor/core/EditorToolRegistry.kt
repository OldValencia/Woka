package com.woka.feature.editor.core

import com.woka.core.data.MediaKind
import com.woka.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The live set of tools, sorted and filtered — the one thing the rail, the
 * inspector, the shortcut table, the settings list and the fold all read.
 *
 * **A `StateFlow`, not a list**, so a tool appearing or disappearing re-renders
 * the editor with no other wiring (§3.4). Building any consumer against a plain
 * list would mean rewriting it the day enablement lands, which is why this comes
 * before the shell rather than after it.
 *
 * The registry never holds the tool set itself: [declared] arrives from Koin's
 * `getAll<EditorTool>()`, so a tool joins by being declared in `EditorModule` and
 * by nothing else. A registry that also hardcoded its members would have two
 * sources of truth (`code-style/di.md`).
 *
 * Built as a `single` and alive for the process, so [scope] is deliberately never
 * cancelled — there is no point at which the app has an editor tool set and no
 * app.
 */
class EditorToolRegistry(
    private val declared: List<EditorTool>,
    settingsRepository: SettingsRepository
) {

    private val scope = CoroutineScope(Dispatchers.Main)

    // One flow per kind, built once. Deriving on every call would start a
    // collector per caller and leak one per recomposition.
    private val byKind: Map<MediaKind, StateFlow<List<EditorTool>>> =
        MediaKind.entries.associateWith { kind ->
            settingsRepository.settings
                .map { visible(kind, it.editorDisabledTools) }
                .distinctUntilChanged()
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    visible(kind, settingsRepository.settings.value.editorDisabledTools)
                )
        }

    /** Enabled tools this media kind supports, in rail and application order. */
    fun tools(kind: MediaKind): StateFlow<List<EditorTool>> = byKind.getValue(kind)

    /**
     * Every declared tool, disabled ones included — what the Settings panel lists.
     * You cannot re-enable what is not shown, which is the whole reason this is
     * separate from [tools].
     */
    fun all(): List<EditorTool> = sorted(declared)

    /**
     * The tool that owns an operation, whether or not it is currently enabled.
     *
     * Enablement is deliberately not consulted: the fold has to tell "no such
     * tool" from "tool switched off" to report the second one properly (§3.4),
     * and it cannot do that against a null that means both.
     */
    fun toolFor(op: ToolOp): EditorTool? = declared.firstOrNull { it.manifest.id == op.toolId }

    private fun visible(kind: MediaKind, disabled: Set<String>): List<EditorTool> =
        sorted(
            declared.filter { tool ->
                kind in tool.manifest.supports && tool.manifest.id !in disabled
            }
        )

    // Domain first, then the tool's own order: the rail draws the groups in
    // `ToolDomain` order and the fold applies operations in it (§3.4), so one
    // sort answers both questions.
    private fun sorted(tools: List<EditorTool>): List<EditorTool> =
        tools.sortedWith(
            compareBy({ it.manifest.domain.ordinal }, { it.manifest.order })
        )
}
