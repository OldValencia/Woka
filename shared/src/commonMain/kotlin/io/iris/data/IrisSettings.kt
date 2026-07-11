package io.iris.data

import kotlinx.serialization.Serializable

enum class ViewLayout { SmallGrid, Grid, BigGrid }
enum class SortType { Custom, NameAsc, NameDesc, DateAsc, DateDesc }

@Serializable
data class IrisSettings(
    val linkedFolders: List<String> = emptyList(),
    val folderLayouts: Map<String, ViewLayout> = emptyMap(),
    val folderSorts: Map<String, SortType> = emptyMap(),
    val folderShowTitles: Map<String, Boolean> = emptyMap(),
    val showTitles: Boolean = true,
    val showFolders: Boolean = true,
    val mediaFilter: String = "All", // All, Photos, Videos
    val theme: String = "System"
)
