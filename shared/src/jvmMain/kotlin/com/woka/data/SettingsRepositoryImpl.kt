package com.woka.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SettingsRepositoryImpl : SettingsRepository {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _settings = MutableStateFlow(WokaSettings())
    override val settings: StateFlow<WokaSettings> = _settings.asStateFlow()

    private val settingsDir = File(System.getProperty("user.home"), "Documents/OldValencia/Woka")
    private val settingsFile = File(settingsDir, "settings.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // Rapid setting changes (e.g. zoom steps while "remember" is on) are coalesced
    // into a single debounced write, collected on ONE coroutine so writes never run
    // concurrently — concurrent writes previously corrupted settings.json.
    private val saveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val writeLock = Any()

    init {
        loadSettings()
        scope.launch {
            saveRequests.collectLatest {
                delay(150)
                writeToDisk(_settings.value)
            }
        }
        // Guarantee a pending (debounced) change is flushed if the app is closed
        // before the 150ms debounce fires.
        Runtime.getRuntime().addShutdownHook(Thread { writeToDisk(_settings.value) })
    }

    private fun loadSettings() {
        if (!settingsFile.exists()) return
        try {
            val text = settingsFile.readText()
            val parsed = json.decodeFromString<WokaSettings>(text)
            _settings.value = parsed
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun addLinkedFolder(path: String) {
        _settings.update { current ->
            if (current.linkedFolders.contains(path)) return@update current
            val newFolders = current.linkedFolders + path
            val newSettings = current.copy(linkedFolders = newFolders)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun removeLinkedFolder(path: String) {
        _settings.update { current ->
            val newFolders = current.linkedFolders - path
            val newSettings = current.copy(linkedFolders = newFolders)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setFolderLayout(path: String, layout: ViewLayout) {
        _settings.update { current ->
            val newMap = current.folderLayouts.toMutableMap()
            newMap[path] = layout
            val newSettings = current.copy(folderLayouts = newMap)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setFolderSort(path: String, sort: SortType) {
        _settings.update { current ->
            val newMap = current.folderSorts.toMutableMap()
            newMap[path] = sort
            val newSettings = current.copy(folderSorts = newMap)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setFolderShowTitles(path: String, show: Boolean) {
        _settings.update { current ->
            val newMap = current.folderShowTitles.toMutableMap()
            newMap[path] = show
            val newSettings = current.copy(folderShowTitles = newMap)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setShowTitles(show: Boolean) {
        _settings.update { current ->
            val newSettings = current.copy(showTitles = show)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setShowHiddenFiles(show: Boolean) {
        _settings.update { current ->
            val newSettings = current.copy(showHiddenFiles = show)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setTheme(theme: String) {
        _settings.update { current ->
            val newSettings = current.copy(theme = theme)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setCarouselMode(mode: CarouselVisibility) {
        _settings.update { current ->
            val newSettings = current.copy(mediaViewerCarouselMode = mode)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setScalePreset(preset: ScalePreset) {
        _settings.update { current ->
            val newSettings = current.copy(scalePreset = preset)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setZoomPopoverView(view: ZoomPopoverView) {
        _settings.update { current ->
            val newSettings = current.copy(zoomPopoverView = view)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setRememberSizeState(remember: Boolean) {
        _settings.update { current ->
            val newSettings = current.copy(rememberSizeState = remember)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setDefaultUseZoom(useZoom: Boolean) {
        _settings.update { current ->
            val newSettings = current.copy(defaultUseZoom = useZoom)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun setDefaultZoomPercent(percent: Int) {
        _settings.update { current ->
            val newSettings = current.copy(defaultZoomPercent = percent.coerceIn(10, 1000))
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun addPinnedSession(session: PinnedSession) {
        _settings.update { current ->
            val newSessions = current.pinnedSessions.filter { it.path != session.path } + session
            val newSettings = current.copy(pinnedSessions = newSessions)
            saveSettings(newSettings)
            newSettings
        }
    }

    override fun removePinnedSession(path: String) {
        _settings.update { current ->
            val newSessions = current.pinnedSessions.filter { it.path != path }
            val newSettings = current.copy(pinnedSessions = newSessions)
            saveSettings(newSettings)
            newSettings
        }
    }

    // Coalesces writes; the debounced collector writes the latest snapshot.
    private fun saveSettings(newSettings: WokaSettings) {
        saveRequests.tryEmit(Unit)
    }

    private fun writeToDisk(current: WokaSettings) = synchronized(writeLock) {
        try {
            if (!settingsDir.exists()) settingsDir.mkdirs()
            val text = json.encodeToString(current)
            // Write to a temp file then atomically replace, so a reader (or a crash
            // mid-write) can never see a half-written / duplicated file.
            val tmp = File(settingsDir, "settings.json.tmp")
            tmp.writeText(text)
            try {
                Files.move(
                    tmp.toPath(),
                    settingsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: Exception) {
                // Fallback if atomic move is unsupported; still serialized, so safe.
                settingsFile.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
