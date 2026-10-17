package com.woka.feature.folder

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.woka.app.AppDependencies
import com.woka.app.shell.sidebar.samePath
import com.woka.core.data.PinnedSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds

/**
 * Where each folder was left, for the life of the run (SIDE-003).
 *
 * It replaces what Back was mostly used for: with history gone, leaving a folder for
 * its sub-folder and coming back through the tree or the path has to land where the
 * user was, not at the top. A Koin `single`, so it outlives every folder screen; not
 * persisted, because the next launch reopens one folder and `lastSession` covers it.
 */
class FolderScrollMemory {
    private val places = MutableStateFlow<Map<String, PinnedSession>>(emptyMap())

    fun placeOf(path: String): PinnedSession? = places.value[path]

    fun store(place: PinnedSession) {
        places.update { it + (place.path to place) }
    }
}

/**
 * The grid's scroll state for [path], restored and recorded.
 *
 * - **Restored** from, in order: this run's memory, the folder's pin, the place the
 *   last session left off (SIDE-002).
 * - **Recorded** as the scroll settles, never at exit, because closing is not a
 *   moment the app can rely on, and `SettingsStore` flushes the last pending write
 *   on close. The record goes to the run's memory and to `lastSession`. The first
 *   emission records the arrival itself. The effect is cancelled when this folder
 *   leaves composition, so an outgoing folder never overwrites the incoming one.
 * - **A pin** gets its scroll written back on the way out, as it always did.
 */
@Composable
internal fun rememberFolderGridState(path: String): LazyGridState {
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val memory = remember { AppDependencies.scrollMemory }
    val start = remember(path) {
        val settings = settingsRepo.settings.value
        memory.placeOf(path)
            ?: settings.pinnedSessions.find { it.path == path }
            ?: settings.lastSession?.takeIf { samePath(it.path, path) }
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = start?.scrollIndex ?: 0,
        initialFirstVisibleItemScrollOffset = start?.scrollOffset ?: 0
    )

    DisposableEffect(path) {
        onDispose {
            if (settingsRepo.settings.value.pinnedSessions.any { it.path == path }) {
                settingsRepo.addPinnedSession(gridState.placeIn(path))
            }
        }
    }

    LaunchedEffect(path) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collectLatest {
                delay(500.milliseconds)
                val place = gridState.placeIn(path)
                memory.store(place)
                settingsRepo.setLastSession(place)
            }
    }
    return gridState
}

/** Where this grid is scrolled to, as the record every consumer above stores. */
internal fun LazyGridState.placeIn(path: String): PinnedSession =
    PinnedSession(path, firstVisibleItemIndex, firstVisibleItemScrollOffset)
