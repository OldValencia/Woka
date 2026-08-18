package com.woka.feature.editor

import androidx.compose.runtime.Composable

/**
 * The editor, mounted over whatever is underneath it.
 *
 * `expect`/`actual` for the same reason `NativeMediaViewer` is: the screen that mounts
 * it (`FolderScreen`) lives in `commonMain`, while everything the editor draws with —
 * Skia surfaces, the raster fold, the save path — is JVM. The seam is here rather than
 * a hundred `internal`s being widened.
 *
 * [target] is **nullable rather than the call site branching on it**: an editor that
 * vanishes the instant its target is cleared has nothing left to play an exit
 * animation with. Null means "not editing", and the actual side keeps the last target
 * alive for exactly as long as it takes to slide away.
 *
 * [onSaved] carries the written path, so the listing is refreshed against it rather
 * than re-scanned hopefully.
 */
@Composable
expect fun NativeMediaEditor(
    target: EditorTarget?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
)
