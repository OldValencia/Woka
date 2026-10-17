package com.woka.core.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchService

/**
 * `WatchService` behind `directoryChanges` (SIDE-007).
 *
 * The service is opened inside the flow, when collection starts, so a flow built
 * and never collected holds nothing open. The blocking `take()` runs on
 * `Dispatchers.IO`; closing the service in `awaitClose` is what ends it — `take()`
 * then throws [ClosedWatchServiceException], which is the way out rather than a
 * fault, and is swallowed as one.
 *
 * Every event in a batch collapses into one emission; the caller debounces the rest,
 * because one screenshot arrives as a creation followed by several modifications.
 */
actual fun directoryChanges(path: String): Flow<Unit> = callbackFlow {
    val watcher = openWatch(path)
    if (watcher == null) {
        close()
        return@callbackFlow
    }
    launch(Dispatchers.IO) {
        try {
            while (true) {
                val key = watcher.take()
                if (key.pollEvents().isNotEmpty()) trySend(Unit)
                if (!key.reset()) break
            }
        } catch (_: ClosedWatchServiceException) {
            // The collector has gone and `awaitClose` closed the service.
        } catch (_: InterruptedException) {
            // The IO thread was interrupted on shutdown; there is nothing to report.
        }
    }
    awaitClose { watcher.close() }
}

/** Null for a path the platform will not watch — the caller's flow is then empty. */
private fun openWatch(path: String): WatchService? {
    val service = FileSystems.getDefault().newWatchService()
    return try {
        Paths.get(path).register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW)
        service
    } catch (_: IOException) {
        service.close()
        null
    } catch (_: UnsupportedOperationException) {
        service.close()
        null
    }
}
