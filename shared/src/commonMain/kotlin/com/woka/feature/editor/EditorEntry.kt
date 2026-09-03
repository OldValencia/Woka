package com.woka.feature.editor

import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditorEngineFactory

/**
 * What both doors hand the editor: one path and one frame.
 *
 * In `commonMain` because the grid's context menu and `FolderScreen` are, and both need
 * to speak this. Nothing here is JVM-specific — `EditorEngineFactory` is a `commonMain`
 * interface precisely so the decision can live above the engine that answers it.
 *
 * [frameIndex] is carried from the start and both callers pass 0 today. Editing frame
 * N of a burst goes through ME-108's conversion and what the carousel shows afterwards
 * is ME-126 — the parameter exists now so neither of those has to change this type.
 */
data class EditorTarget(
    val path: String,
    val frameIndex: Int = 0
)

/**
 * Whether this file can be edited — asked by the viewer's control pill and by the
 * grid's context menu, answered in one place.
 *
 * **The factory is the decision** (§4.1). It routes on `File.mediaKind()`, which is the
 * app's single answer to what a file is, and returns null for anything it does not
 * recognise. **A video is recognised since ME-201** — it opens with the picture tools
 * live and cannot be saved yet, which is what `EditCapability.Preview` says. Neither
 * door may grow its own extension list; that is the mistake `MediaKind` was extracted
 * to end.
 *
 * Cheap enough to call while drawing a menu: the answer is a question about the
 * **container**, so it costs a stat and reads no pixel. A path that is not there, or
 * whose format can neither carry a recipe nor be re-encoded (§5.1), answers false —
 * before any work is done rather than after it.
 */
fun isEditable(factory: EditorEngineFactory, path: String): Boolean =
    runCatching { factory.capability(path) != EditCapability.Unavailable }.getOrDefault(false)
