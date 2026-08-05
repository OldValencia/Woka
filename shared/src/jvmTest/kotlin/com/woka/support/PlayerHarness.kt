package com.woka.support

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.fixtures.SampleMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * A real decode engine over a real file. Nothing here is virtual: the grabber runs
 * on `Dispatchers.IO` and frames arrive on threads of their own, so callers poll
 * rather than advance a clock. Each player gets its own scope, cancelled in
 * `finally`, so a failing test cannot leave a decode loop running into the next.
 */
fun <T> onPlayer(file: File, block: (VideoPlayerState) -> T): T {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val player = VideoPlayerState(scope, file, initialVolume = 0f)
    return try {
        block(player)
    } finally {
        player.dispose()
        scope.cancel()
    }
}

/** As [onPlayer], but the duration is known first — the transport clamps to it. */
fun <T> onOpenPlayer(file: File, block: (VideoPlayerState) -> T): T = onPlayer(file) { player ->
    awaitTrue("a duration to be read", timeoutMs = 20_000L) { player.durationMs > 0L }
    block(player)
}

/** The single-audio-track episode: the simplest thing in the corpus that plays. */
fun episodeTwoFile(): File = SampleMedia.file(SampleMedia.episode(2, ".mkv"))
