package com.woka.feature.viewer.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Persistent, because a timer would leave an unsupported film looking as if it still loads. */
@Composable
internal fun UnsupportedVideoNotice(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NoticeCard(text = message)
    }
}
