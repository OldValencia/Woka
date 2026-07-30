package com.woka.data

import kotlinx.serialization.Serializable

enum class ViewLayout { SmallGrid, Grid, BigGrid }
enum class SortType { Custom, NameAsc, NameDesc, DateAsc, DateDesc }
enum class CarouselVisibility { AUTO, ALWAYS_ON, HIDDEN }
enum class ScalePreset { Fit, Fill, Original }
enum class ZoomPopoverView { Slider, Input }

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
    val defaultZoomPercent: Int = 100
)
