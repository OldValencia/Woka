package com.woka.feature.viewer.video.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A stable per-player rejection, published on Main; no per-frame state or retained native data. */
internal class VideoPlaybackFailure {
    var message by mutableStateOf<String?>(null)
        internal set
}
