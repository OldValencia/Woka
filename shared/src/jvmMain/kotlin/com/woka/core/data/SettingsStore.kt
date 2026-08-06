package com.woka.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Duration.Companion.milliseconds

/**
 * Disk layer behind [SettingsRepositoryImpl]: where `settings.json` lives, how it
 * is parsed, and how writes are coalesced and made atomic. Split out per the §2
 * file-size mandate, leaving the repository holding only the setters.
 */
internal class SettingsStore(private val scope: CoroutineScope) {
    private val settingsFile = WokaStorage.settingsFile

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // Rapid setting changes (e.g. zoom steps while "remember" is on) are coalesced
    // into a single debounced write, collected on ONE coroutine so writes never run
    // concurrently — concurrent writes previously corrupted settings.json.
    //
    // `replay = 1` because [start] subscribes from a coroutine on Dispatchers.IO:
    // a requestSave() in the window before that collector exists reached nobody and
    // was dropped (F9). The cache is empty until the first emission, so this cannot
    // manufacture a write of its own, and it lives no longer than this instance.
    private val saveRequests = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val writeLock = Any()

    /** Reads the persisted settings, or null when absent / unreadable. */
    fun load(): WokaSettings? {
        if (!settingsFile.exists()) return null
        return try {
            migrateSizeSchema(json.decodeFromString<WokaSettings>(settingsFile.readText()))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Step 8b re-based the zoom percentage on native 1:1 pixels; records written
     * under the old percent-of-fitted base mean a different size and cannot be
     * converted (the fitted size depends on a panel that does not exist at load
     * time). Reset the default once and move on.
     *
     * Only ever reached with a file in hand: a fresh install starts at
     * [SIZE_SCHEMA_VERSION] (see `SettingsRepositoryImpl`), so the absent-field 0
     * below means "written before the field existed" and nothing else.
     */
    private fun migrateSizeSchema(loaded: WokaSettings): WokaSettings {
        if (loaded.sizeSchemaVersion >= SIZE_SCHEMA_VERSION) return loaded
        return loaded.copy(
            defaultUseZoom = false,
            scalePreset = ScalePreset.Fit,
            defaultZoomPercent = 100,
            sizeSchemaVersion = SIZE_SCHEMA_VERSION
        )
    }

    /** Starts the debounced writer; [snapshot] supplies the latest state to write. */
    fun start(snapshot: () -> WokaSettings) {
        scope.launch {
            saveRequests.collectLatest {
                delay(150.milliseconds)
                writeToDisk(snapshot())
            }
        }
        // Guarantee a pending (debounced) change is flushed if the app is closed
        // before the 150ms debounce fires.
        Runtime.getRuntime().addShutdownHook(Thread { writeToDisk(snapshot()) })
    }

    /** Requests a save; writes are coalesced, the collector persists the latest. */
    fun requestSave() {
        saveRequests.tryEmit(Unit)
    }

    private fun writeToDisk(current: WokaSettings) = synchronized(writeLock) {
        try {
            if (!WokaStorage.root.exists()) WokaStorage.root.mkdirs()
            val text = json.encodeToString(current)
            // Write to a temp file then atomically replace, so a reader (or a crash
            // mid-write) can never see a half-written / duplicated file.
            val tmp = File(WokaStorage.root, "settings.json.tmp")
            tmp.writeText(text)
            try {
                Files.move(
                    tmp.toPath(),
                    settingsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                // Fallback if atomic move is unsupported; still serialized, so safe.
                settingsFile.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
