package com.woka.data

import kotlinx.serialization.Serializable

enum class ViewLayout { SmallGrid, Grid, BigGrid }
enum class SortType { Custom, NameAsc, NameDesc, DateAsc, DateDesc }
enum class CarouselVisibility { AUTO, ALWAYS_ON, HIDDEN }

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
    val pinnedSessions: List<PinnedSession> = emptyList()
)
