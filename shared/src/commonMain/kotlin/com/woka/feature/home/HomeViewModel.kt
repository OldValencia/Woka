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
import com.woka.core.data.ViewLayout

/**
 * Home has no path of its own, so its grid density borrows the per-folder map under
 * a reserved key — the same trick `folderShowTitles` already plays for this screen.
 */
private const val HOME_LAYOUT_KEY = "HOME"

data class HomeState(
    val linkedFolders: List<String> = emptyList(),
    val showTitles: Boolean = true,
    val layout: ViewLayout = ViewLayout.Grid
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
                showTitles = settings.folderShowTitles["HOME"] ?: settings.showTitles,
                layout = settings.folderLayouts[HOME_LAYOUT_KEY] ?: ViewLayout.Grid
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

    fun setLayout(layout: ViewLayout) {
        settingsRepository.setFolderLayout(HOME_LAYOUT_KEY, layout)
    }

    fun pinFolder(path: String) {
        val file = File(path)
        if (file.isDirectory) {
            settingsRepository.addPinnedSession(PinnedSession(path = path))
        }
    }
}
