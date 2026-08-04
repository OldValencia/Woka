package com.woka.ui.viewer.player

import androidx.compose.runtime.Immutable

@Immutable
data class Cue(
    val startMs: Long,
    val endMs: Long,
    val text: String? = null,
    val image: SubtitleImage? = null
)
