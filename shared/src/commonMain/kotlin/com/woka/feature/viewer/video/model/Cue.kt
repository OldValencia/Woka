package com.woka.feature.viewer.video.model

import androidx.compose.runtime.Immutable

@Immutable
data class Cue(
    val startMs: Long,
    val endMs: Long,
    val text: String? = null,
    val image: SubtitleImage? = null
)
