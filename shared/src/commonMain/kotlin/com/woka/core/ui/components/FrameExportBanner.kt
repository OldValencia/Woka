package com.woka.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.woka.core.utils.FrameExportStatus
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * What an export is doing, wherever the user happens to be.
 *
 * One banner in the shell rather than one per screen: an export started from
 * the grid keeps running when the viewer opens over it, and a 96-picture
 * container takes twelve seconds — long enough that silence reads as the app
 * having hung.
 *
 * The pill itself is [StatusPill] and its place on screen is `StatusBanners`'
 * (ME-130). What is left here is the half that is this producer's alone: which
 * message settles, and who is told to clear it.
 */
@Composable
fun FrameExportBanner() {
    val message = FrameExportStatus.message
    // Only a finished message clears itself; a running one stays until the next
    // update replaces it, however long that takes.
    LaunchedEffect(message) {
        if (message != null && !message.startsWith("Exporting")) {
            delay(STATUS_SETTLE_MS.milliseconds)
            FrameExportStatus.clear()
        }
    }
    StatusPill(message)
}
