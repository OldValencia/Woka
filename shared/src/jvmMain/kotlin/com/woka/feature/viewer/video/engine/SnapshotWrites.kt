package com.woka.feature.viewer.video.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one way a decode loop writes Compose state: **on the thread that reads it.**
 *
 * Two failures were measured before this existed, and only writing on Main answers both.
 *
 * - The write can be **lost**. Assigning `mutableStateOf` from a background coroutine
 *   writes into the global snapshot, and the recomposer advancing it drops the write —
 *   silently. That opened a file with three audio tracks probed and none of them stored:
 *   no track menu, no seek bar, a crossed-out speaker.
 *
 * - The write can be **unseen**. Wrapping it in `Snapshot.withMutableSnapshot` made it
 *   survive, and the UI still drew the old value whenever the write landed *before* the
 *   composition first read it: there was no read to invalidate, and the composition then
 *   took a snapshot older than the apply. Measured as `composed=false now=true` in the
 *   same log line — the state correct, the screen stale, and nothing to trigger a second
 *   pass until an unrelated click did.
 *
 * On Main both vanish, because this is then an ordinary Compose write, indistinguishable
 * from a button's. The block must therefore be assignments only — anything that touches a
 * disk or a decoder stays on the caller's dispatcher (`code-style/state.md`).
 */
internal suspend fun writeUiState(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
