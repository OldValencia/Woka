package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable

/**
 * Clip layer [key] as it was [before] a history step and [after] it (ME-267), null for a
 * layer the session held nothing of — the file's own, read again on the next visit.
 *
 * Whole [FrameEdit]s rather than ops, for `EditorHistory`'s reason: restoring a snapshot
 * cannot get an inverse wrong.
 */
@Immutable
data class LayerChange(val key: Int, val before: FrameEdit?, val after: FrameEdit?)
