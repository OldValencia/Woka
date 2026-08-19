package com.woka.core.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * "This file is not what you last read" — said once, heard by every reader (ME-135).
 *
 * **Eviction is not invalidation.** A save already drops the file from
 * `ImageBitmapCache` and from Coil's memory (§5.2), and that changes nothing on
 * screen: a cache is consulted only when something asks it, and nothing asks. The
 * viewer's decode is a `LaunchedEffect` keyed on the item and the frame, and while
 * the editor is mounted over it neither of those changes — which is why `Done` used
 * to leave the old picture up until the viewer was left and re-entered.
 *
 * **A holder in `core`, not a parameter.** Threading a reload key from `FolderScreen`
 * down would touch four signatures and still reach only the readers below that call;
 * PiP and the fullscreen window draw the same bitmap from trees of their own. This is
 * the shape `AppliedStillEdits` already established: declared in `core`, written by
 * the editor, read by whoever draws.
 */
object MediaInvalidation {

    /**
     * [generation] is what makes a second save of the same file a second signal
     * rather than an equal value a `StateFlow` drops.
     */
    data class Invalidated(val path: String, val generation: Long)

    private val _latest = MutableStateFlow<Invalidated?>(null)

    val latest: StateFlow<Invalidated?> = _latest.asStateFlow()

    /** Raised **after** the caches are cleared, never before. */
    fun invalidate(path: String) {
        _latest.update { Invalidated(path, (it?.generation ?: 0L) + 1L) }
    }
}

/**
 * A key for [path] that changes when — and only when — that file is invalidated.
 *
 * Added to a decode effect's key list, it is the whole of what a reader has to do.
 * The value is **latched** rather than read straight off the flow: the generation
 * counts every save in the app, so an unrelated file's write would otherwise move
 * this key back to zero and re-decode a picture nothing had touched.
 */
@Composable
fun rememberReloadKey(path: String?): Long {
    var key by remember(path) { mutableStateOf(0L) }
    LaunchedEffect(path) {
        MediaInvalidation.latest.collect {
            if (it != null && it.path == path) key = it.generation
        }
    }
    return key
}
