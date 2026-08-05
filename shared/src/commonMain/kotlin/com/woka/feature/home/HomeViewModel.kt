package com.woka.feature.home

import com.woka.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import com.woka.core.data.PinnedSession

data class HomeState(
    val linkedFolders: List<String> = emptyList(),
    val showTitles: Boolean = true
)

class HomeViewModel(
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    val state: StateFlow<HomeState> = settingsRepository.settings
        .map { settings ->
            val rawFolders = settings.linkedFolders
            val sortedFolders = rawFolders.sortedBy { File(it).name.lowercase() }
            
            HomeState(
                linkedFolders = sortedFolders,
                showTitles = settings.folderShowTitles["HOME"] ?: settings.showTitles
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeState()
        )

    fun removeLinkedFolder(path: String) {
        settingsRepository.removeLinkedFolder(path)
        settingsRepository.removePinnedSession(path)
    }

    fun pinFolder(path: String) {
        val file = File(path)
        if (file.isDirectory) {
            settingsRepository.addPinnedSession(PinnedSession(path = path))
        }
    }
}
