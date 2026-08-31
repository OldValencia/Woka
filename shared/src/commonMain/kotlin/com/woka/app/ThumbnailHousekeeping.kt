package com.woka.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.woka.core.data.sweepThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/**
 * How long the sweep waits before touching the disk.
 *
 * A `LaunchedEffect` already runs after the first composition is applied, so this is
 * not about the first frame — it is about the burst behind it. Opening Home decodes a
 * cover per folder card, and those run two at a time on `ImageBitmapCache.ioDispatcher`;
 * a directory walk over thousands of entries in the middle of that is contention for
 * nothing. Nothing on screen depends on the sweep having happened.
 */
private val SWEEP_DELAY = 5.seconds

/**
 * Brings `.thumbnails` back under its budget, once per launch, off the critical path.
 *
 * Draws nothing and is not a screen — it is here rather than in `main()` because the
 * window is what defines "the app is up and the first screen is drawn", and because a
 * composition-scoped effect is cancelled with the window instead of outliving it.
 *
 * The result is deliberately dropped: there is no logger in this project, and a sweep
 * that removed nothing is the ordinary case rather than something to report. What it
 * did is observable in `ThumbnailSweepTest`, which calls [sweepThumbnails] directly.
 */
@Composable
internal fun ThumbnailHousekeeping() {
    LaunchedEffect(Unit) {
        delay(SWEEP_DELAY)
        withContext(Dispatchers.IO) { sweepThumbnails() }
    }
}
