package com.woka.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class SettingsRepositoryImpl : SettingsRepository {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _settings = MutableStateFlow(WokaSettings())
    override val settings: StateFlow<WokaSettings> = _settings.asStateFlow()

    // Disk access, debouncing and atomic writes live in SettingsStore (§2 size mandate).
    private val store = SettingsStore(scope)

    init {
        store.load()?.let { _settings.value = it }
        store.start { _settings.value }
    }

    override fun addLinkedFolder(path: String) {
        _settings.update { current ->
            // Guarded rather than routed through [update]: re-adding an existing
            // folder must not touch state or schedule a pointless write.
            if (current.linkedFolders.contains(path)) return@update current
            store.requestSave()
            current.copy(linkedFolders = current.linkedFolders + path)
        }
    }

    override fun removeLinkedFolder(path: String) = update { current ->
        // Unlinking must leave no stale track records behind (§2.6 cleanup): drop
        // the folder's own record plus every per-file record under it.
        val prefix = path.trimEnd(File.separatorChar) + File.separatorChar
        current.copy(
            linkedFolders = current.linkedFolders - path,
            trackPrefsPerFile = current.trackPrefsPerFile.filterKeys { !it.startsWith(prefix) },
            trackPrefsPerFolder = current.trackPrefsPerFolder
                .filterKeys { it != path && !it.startsWith(prefix) }
        )
    }

    override fun setFolderLayout(path: String, layout: ViewLayout) =
        update { it.copy(folderLayouts = it.folderLayouts + (path to layout)) }

    override fun setFolderSort(path: String, sort: SortType) =
        update { it.copy(folderSorts = it.folderSorts + (path to sort)) }

    override fun setFolderShowTitles(path: String, show: Boolean) =
        update { it.copy(folderShowTitles = it.folderShowTitles + (path to show)) }

    override fun setShowTitles(show: Boolean) = update { it.copy(showTitles = show) }

    override fun setShowHiddenFiles(show: Boolean) = update { it.copy(showHiddenFiles = show) }

    override fun setTheme(theme: String) = update { it.copy(theme = theme) }

    override fun setCarouselMode(mode: CarouselVisibility) =
        update { it.copy(mediaViewerCarouselMode = mode) }

    override fun setScalePreset(preset: ScalePreset) = update { it.copy(scalePreset = preset) }

    override fun setZoomPopoverView(view: ZoomPopoverView) =
        update { it.copy(zoomPopoverView = view) }

    override fun setRememberSizeState(remember: Boolean) =
        update { it.copy(rememberSizeState = remember) }

    override fun setDefaultUseZoom(useZoom: Boolean) = update { it.copy(defaultUseZoom = useZoom) }

    override fun setDefaultZoomPercent(percent: Int) =
        update { it.copy(defaultZoomPercent = percent.coerceIn(10, 1000)) }

    override fun setSubtitleSize(size: SubtitleSize) = update { it.copy(subtitleSize = size) }

    override fun setSubtitleFont(font: SubtitleFont) = update { it.copy(subtitleFont = font) }

    override fun setSubtitleColor(argb: Long) = update { it.copy(subtitleColorArgb = argb) }

    override fun setSubtitleBoxOpacity(opacity: Float) =
        update { it.copy(subtitleBoxOpacity = opacity.coerceIn(0f, 1f)) }

    override fun setSubtitleOutline(outline: Boolean) = update { it.copy(subtitleOutline = outline) }

    override fun resetSubtitleAppearance() {
        val d = WokaSettings()
        update {
            it.copy(
                subtitleSize = d.subtitleSize,
                subtitleFont = d.subtitleFont,
                subtitleColorArgb = d.subtitleColorArgb,
                subtitleBoxOpacity = d.subtitleBoxOpacity,
                subtitleOutline = d.subtitleOutline
            )
        }
    }

    override fun setMediaVolume(volume: Float) =
        update { it.copy(mediaVolume = volume.coerceIn(0f, 1f)) }

    override fun addPinnedSession(session: PinnedSession) = update { current ->
        current.copy(
            pinnedSessions = current.pinnedSessions.filter { it.path != session.path } + session
        )
    }

    override fun removePinnedSession(path: String) = update { current ->
        current.copy(pinnedSessions = current.pinnedSessions.filter { it.path != path })
    }

    override fun setTrackPrefs(filePath: String, folderPath: String, prefs: TrackPrefs) =
        update { current ->
            current.copy(
                trackPrefsPerFile = current.trackPrefsPerFile + (filePath to prefs),
                // The folder layer never carries video (§2.6): another episode's
                // second video stream is a different encode, not the same track.
                trackPrefsPerFolder = current.trackPrefsPerFolder +
                    (folderPath to prefs.copy(video = null))
            )
        }

    override fun clearTrackPrefsForFile(filePath: String) =
        update { it.copy(trackPrefsPerFile = it.trackPrefsPerFile - filePath) }

    // The update-copy-save pattern every setter goes through.
    private inline fun update(transform: (WokaSettings) -> WokaSettings) {
        _settings.update { current ->
            store.requestSave()
            transform(current)
        }
    }
}
