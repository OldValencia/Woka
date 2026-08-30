package com.woka.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.woka.core.ui.components.STATUS_SETTLE_MS
import com.woka.core.ui.components.StatusPill
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * What `Done` is doing, once the editor it was pressed in has closed (ME-130).
 *
 * The mirror of `FrameExportBanner`, and deliberately as small: the pill is
 * [StatusPill]'s, the placement is `StatusBanners`', and what is this producer's own is
 * the settle rule — a running save holds the pill up for as long as the write takes,
 * and only its answer takes itself away.
 */
@Composable
fun EditorSaveBanner() {
    val message = EditorSaveStatus.message
    LaunchedEffect(message) {
        if (message != null && !message.startsWith(SAVING_PREFIX)) {
            delay(STATUS_SETTLE_MS.milliseconds)
            EditorSaveStatus.clear()
        }
    }
    StatusPill(message)
}
