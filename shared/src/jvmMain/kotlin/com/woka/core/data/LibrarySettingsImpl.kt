package com.woka.core.data

import java.io.File

/**
 * The [LibrarySettings] half of the repository: folders, listing, appearance and
 * subtitle styling. Holds no state of its own — [mutator] is shared with
 * [PlaybackSettingsImpl] so both halves write one record.
 */
internal class LibrarySettingsImpl(private val mutator: SettingsMutator) : LibrarySettings {

    override fun addLinkedFolder(path: String) = mutator.updateIf { current ->
        // Guarded rather than routed through `update`: re-adding an existing
        // folder must not touch state or schedule a pointless write.
        if (current.linkedFolders.contains(path)) null
        else current.copy(linkedFolders = current.linkedFolders + path)
    }

    override fun removeLinkedFolder(path: String) =
        mutator.update { it.withoutFolder(path, File.separatorChar) }

    override fun setFolderLayout(path: String, layout: ViewLayout) =
        mutator.update { it.copy(folderLayouts = it.folderLayouts + (path to layout)) }

    override fun setFolderSort(path: String, sort: SortType) =
        mutator.update { it.copy(folderSorts = it.folderSorts + (path to sort)) }

    override fun setFolderShowTitles(path: String, show: Boolean) =
        mutator.update { it.copy(folderShowTitles = it.folderShowTitles + (path to show)) }

    override fun setShowTitles(show: Boolean) = mutator.update { it.copy(showTitles = show) }

    override fun setShowHiddenFiles(show: Boolean) =
        mutator.update { it.copy(showHiddenFiles = show) }

    override fun setTheme(theme: String) = mutator.update { it.copy(theme = theme) }

    override fun setCarouselMode(mode: CarouselVisibility) =
        mutator.update { it.copy(mediaViewerCarouselMode = mode) }

    override fun setAmbientFill(enabled: Boolean) = mutator.update { it.copy(ambientFill = enabled) }

    override fun setScalePreset(preset: ScalePreset) =
        mutator.update { it.copy(scalePreset = preset) }

    override fun setHdrViewMode(mode: HdrViewMode) =
        mutator.update { it.copy(hdrViewMode = mode) }

    override fun setRememberSizeState(remember: Boolean) =
        mutator.update { it.copy(rememberSizeState = remember) }

    override fun setDefaultUseZoom(useZoom: Boolean) =
        mutator.update { it.copy(defaultUseZoom = useZoom) }

    override fun setDefaultZoomPercent(percent: Int) =
        mutator.update { it.copy(defaultZoomPercent = percent.coerceIn(10, 1600)) }

    override fun setSubtitleSize(size: SubtitleSize) =
        mutator.update { it.copy(subtitleSize = size) }

    override fun setSubtitleFont(font: SubtitleFont) =
        mutator.update { it.copy(subtitleFont = font) }

    override fun setSubtitleColor(argb: Long) =
        mutator.update { it.copy(subtitleColorArgb = argb) }

    override fun setSubtitleBoxOpacity(opacity: Float) =
        mutator.update { it.copy(subtitleBoxOpacity = opacity.coerceIn(0f, 1f)) }

    override fun setSubtitleOutline(outline: Boolean) =
        mutator.update { it.copy(subtitleOutline = outline) }

    override fun resetSubtitleAppearance() =
        mutator.update { it.withDefaultSubtitleAppearance() }

    override fun addPinnedSession(session: PinnedSession) =
        mutator.update { it.withPinnedSession(session) }

    override fun removePinnedSession(path: String) = mutator.update { current ->
        current.copy(pinnedSessions = current.pinnedSessions.filter { it.path != path })
    }
}
