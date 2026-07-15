package io.iris.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SettingsRepositoryImpl : SettingsRepository {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _settings = MutableStateFlow(IrisSettings())
    override val settings: StateFlow<IrisSettings> = _settings.asStateFlow()

    private val settingsDir = File(System.getProperty("user.home"), "Documents/OldValencia/Iris")
    private val settingsFile = File(settingsDir, "settings.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        loadSettings()
    }

    private fun loadSettings() {
        if (!settingsFile.exists()) return
        try {
            val text = settingsFile.readText()
            val parsed = json.decodeFromString<IrisSettings>(text)
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

    private fun saveSettings(newSettings: IrisSettings) {
        scope.launch {
            try {
                if (!settingsDir.exists()) {
                    settingsDir.mkdirs()
                }
                settingsFile.writeText(json.encodeToString(newSettings))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
