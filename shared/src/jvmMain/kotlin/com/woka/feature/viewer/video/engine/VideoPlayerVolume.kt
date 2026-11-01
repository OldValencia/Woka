package com.woka.feature.viewer.video.engine

import com.woka.feature.viewer.video.engine.audio.lineLevel
import com.woka.feature.viewer.video.engine.decode.setVolumeInternal

/**
 * Sets the player's volume and applies it to the open sound line. Moved out of
 * [VideoPlayerState] when that class reached the file-size mandate (VF-102).
 */
fun VideoPlayerState.updateVolume(newVolume: Float) {
    volume = newVolume
    soundLineInstance?.let { line ->
        setVolumeInternal(line, lineLevel(newVolume))
    }
}
