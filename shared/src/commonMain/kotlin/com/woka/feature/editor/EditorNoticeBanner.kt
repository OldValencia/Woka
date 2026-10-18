package com.woka.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.woka.core.ui.components.STATUS_SETTLE_MS
import com.woka.core.ui.components.StatusPill
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Transient status pill displayed when editing actions cannot proceed
 * (e.g. attempting to edit read-only media like PSD).
 */
@Composable
fun EditorNoticeBanner() {
    val message = EditorNoticeStatus.message
    LaunchedEffect(message) {
        if (message != null) {
            delay(STATUS_SETTLE_MS.milliseconds)
            EditorNoticeStatus.clear()
        }
    }
    StatusPill(message)
}
