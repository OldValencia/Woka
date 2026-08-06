package com.woka.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class SettingsRepositoryImpl : SettingsRepository {
    private val scope = CoroutineScope(Dispatchers.IO)
    // Seeded at the current schema version, not at the field's default: with no
    // file behind it there is nothing to migrate, and leaving it at 0 made the
    // next launch treat this session's own writes as pre-Step-8b records (F8).
    private val _settings = MutableStateFlow(WokaSettings(sizeSchemaVersion = SIZE_SCHEMA_VERSION))
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

    override fun removeLinkedFolder(path: String) =
        update { it.withoutFolder(path, File.separatorChar) }

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

    override fun setRememberSizeState(remember: Boolean) =
        update { it.copy(rememberSizeState = remember) }

    override fun setDefaultUseZoom(useZoom: Boolean) = update { it.copy(defaultUseZoom = useZoom) }

    override fun setDefaultZoomPercent(percent: Int) =
        update { it.copy(defaultZoomPercent = percent.coerceIn(10, 1600)) }

    override fun setSubtitleSize(size: SubtitleSize) = update { it.copy(subtitleSize = size) }

    override fun setSubtitleFont(font: SubtitleFont) = update { it.copy(subtitleFont = font) }

    override fun setSubtitleColor(argb: Long) = update { it.copy(subtitleColorArgb = argb) }

    override fun setSubtitleBoxOpacity(opacity: Float) =
        update { it.copy(subtitleBoxOpacity = opacity.coerceIn(0f, 1f)) }

    override fun setSubtitleOutline(outline: Boolean) = update { it.copy(subtitleOutline = outline) }

    override fun resetSubtitleAppearance() = update { it.withDefaultSubtitleAppearance() }

    override fun setMediaVolume(volume: Float) =
        update { it.copy(mediaVolume = volume.coerceIn(0f, 1f)) }

    override fun addPinnedSession(session: PinnedSession) =
        update { it.withPinnedSession(session) }

    override fun removePinnedSession(path: String) = update { current ->
        current.copy(pinnedSessions = current.pinnedSessions.filter { it.path != path })
    }

    override fun setTrackPrefs(
        filePath: String,
        folderPath: String,
        filePrefs: TrackPrefs,
        folderPrefs: TrackPrefs
    ) = update { it.withTrackPrefs(filePath, folderPath, filePrefs, folderPrefs) }

    override fun clearTrackPrefsForFile(filePath: String) =
        update { it.copy(trackPrefsPerFile = it.trackPrefsPerFile - filePath) }

    /**
     * The escape hatch for a library-wide change of mind (Step 15): without it,
     * switching a whole season to another dub would mean visiting every file that
     * ever had an explicit choice.
     */
    override fun clearAllTrackPrefs() =
        update { it.copy(trackPrefsPerFile = emptyMap(), trackPrefsPerFolder = emptyMap()) }

    override fun setRepeatMode(mode: RepeatMode) = update { it.copy(repeatMode = mode) }

    override fun setShuffle(shuffle: Boolean) = update { it.copy(shuffle = shuffle) }

    override fun setPlaybackSpeed(speed: Float) =
        update { it.copy(playbackSpeed = PlaybackSpeed.snap(speed)) }

    override fun setResumeMode(mode: ResumeMode) = update { it.copy(resumeMode = mode) }

    override fun setResumeMinDuration(minutes: Int) =
        update { it.copy(resumeMinDurationMinutes = minutes.coerceIn(1, 240)) }

    override fun setPlaybackPosition(filePath: String, positionMs: Long) =
        update { it.copy(playbackPositions = it.playbackPositions + (filePath to positionMs)) }

    override fun clearPlaybackPosition(filePath: String) =
        update { it.copy(playbackPositions = it.playbackPositions - filePath) }

    override fun setCarryTracksAcrossFolder(enabled: Boolean) =
        update { it.copy(carryTracksAcrossFolder = enabled) }

    override fun setSystemMediaControls(enabled: Boolean) =
        update { it.copy(systemMediaControls = enabled) }

    // The update-copy-save pattern every setter goes through.
    private inline fun update(transform: (WokaSettings) -> WokaSettings) {
        _settings.update { current ->
            store.requestSave()
            transform(current)
        }
    }
}
