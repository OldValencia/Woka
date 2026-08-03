package com.woka.data

import kotlinx.serialization.Serializable

enum class ViewLayout { SmallGrid, Grid, BigGrid }
enum class SortType { Custom, NameAsc, NameDesc, DateAsc, DateDesc }
enum class CarouselVisibility { AUTO, ALWAYS_ON, HIDDEN }
enum class ScalePreset { Fit, Fill, Original }
enum class ZoomPopoverView { Slider, Input }
// Subtitle appearance (§3). Auto scales the size from the video-panel height; the
// other steps are fixed multipliers of that same relative base, so the user's
// choice stays proportional on window resize instead of being reset.
enum class SubtitleSize { Auto, Small, Medium, Large, ExtraLarge }
enum class SubtitleFont { System, Serif, Monospace }

@Serializable
data class PinnedSession(
    val path: String,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)

@Serializable
data class WokaSettings(
    val linkedFolders: List<String> = emptyList(),
    val folderLayouts: Map<String, ViewLayout> = emptyMap(),
    val folderSorts: Map<String, SortType> = emptyMap(),
    val folderShowTitles: Map<String, Boolean> = emptyMap(),
    val showTitles: Boolean = true,
    val showFolders: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val mediaFilter: String = "All", // All, Photos, Videos
    val theme: String = "System",
    val mediaViewerCarouselMode: CarouselVisibility = CarouselVisibility.AUTO,
    val pinnedSessions: List<PinnedSession> = emptyList(),
    val scalePreset: ScalePreset = ScalePreset.Fit,
    val zoomPopoverView: ZoomPopoverView = ZoomPopoverView.Slider,
    // Default size state applied when a video opens. If [rememberSizeState] is on,
    // adjusting zoom/preset inside a video writes back into these fields.
    val rememberSizeState: Boolean = false,
    val defaultUseZoom: Boolean = false,
    val defaultZoomPercent: Int = 100,
    // Last media-viewer volume (0f–1f), restored across files and app restarts.
    val mediaVolume: Float = 0.5f,
    // Subtitle appearance (§3), applied to text subtitles. Persisted globally.
    val subtitleSize: SubtitleSize = SubtitleSize.Auto,
    val subtitleFont: SubtitleFont = SubtitleFont.System,
    val subtitleColorArgb: Long = 0xFFFFFFFFL,
    val subtitleBoxOpacity: Float = 0.75f,
    val subtitleOutline: Boolean = true,
    // Remembered track choices (§2.6). Per absolute file path is authoritative;
    // the per-folder layer seeds the next episode of a series (audio + subtitles
    // only — a second video stream in another file is a different angle, not the
    // same track).
    val trackPrefsPerFile: Map<String, TrackPrefs> = emptyMap(),
    val trackPrefsPerFolder: Map<String, TrackPrefs> = emptyMap(),
    // Playback modes (§6), global rather than per-file: they are how the user
    // likes to watch, not a property of any one video.
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    val playbackSpeed: Float = PlaybackSpeed.NORMAL
)
