package com.woka.core.utils

import kotlinx.coroutines.flow.Flow

/**
 * One emission per batch of changes directly inside [path] — an entry created,
 * deleted or modified, or the platform saying it lost count (SIDE-007).
 *
 * Cold: collecting opens the watch and cancelling the collector closes it, so its
 * lifetime is whoever collects it — the folder on screen, never a ViewModel whose
 * scope outlives the screen. A path that cannot be watched, such as a network
 * share that refuses, is an empty flow and the caller simply never refreshes.
 *
 * Changes inside sub-folders are not reported: the grid lists one level, and a
 * recursive watch over a film library would wake on every subtitle download.
 */
expect fun directoryChanges(path: String): Flow<Unit>
