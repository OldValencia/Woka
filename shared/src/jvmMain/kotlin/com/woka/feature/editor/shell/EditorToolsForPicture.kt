package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.woka.core.data.StillColorInfo
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry

/**
 * The rail this **file** gets, which is the registry's answer minus the tools that have
 * nothing to say about it (ME-302).
 *
 * **A filter over the registry, never a widening of it.** `EditorToolRegistry.tools`
 * answers two questions — does the medium support this tool, and is it switched on in
 * Settings — and `EditorSkippedOps` and `RasterFold.resolve` both read that answer to
 * tell "no such tool" from "tool switched off, its work was not applied". A third
 * meaning folded into it would make the skipped-ops notice claim a *hidden* tool's
 * operations were dropped, and §3.4's promise is that the notice never lies.
 *
 * The engine answers for the file rather than the shell reading it, for the reason the
 * whole seam exists (§3.3): the shell would otherwise have to name `MediaKind` to avoid
 * asking a video a still's question, and a video inherits the SDR default instead.
 */
@Composable
internal fun toolsForPicture(
    registry: EditorToolRegistry,
    engine: EditorEngine
): List<EditorTool> {
    val enabled by registry.tools(engine.kind).collectAsState()
    // **The `remember` is load-bearing.** `EditorScreen` keys a `LaunchedEffect` on this
    // list to clear a tool that has vanished; a fresh `filter` per recomposition changes
    // the list's identity and would restart that effect several times a second.
    return remember(enabled, engine) { enabled.forPicture(engine.sourceColor) }
}

/**
 * Pure, and separate from the composable above so it can be asserted on without a
 * window: what is being proved is that the shell asks every tool rather than naming one.
 */
internal fun List<EditorTool>.forPicture(color: StillColorInfo): List<EditorTool> =
    filter { it.appliesTo(color) }
