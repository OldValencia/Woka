package com.woka.app.shell

import com.woka.app.shell.sidebar.UnlinkUndoBanner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.FrameExportBanner
import com.woka.feature.editor.EditorNoticeBanner
import com.woka.feature.editor.EditorSaveBanner

/**
 * Every status pill in the app, in one place at the bottom of the window (ME-130).
 *
 * Both producers wanted the same spot — an export started in the grid keeps running
 * while a photograph is edited over it, and `Done` answers from the same corner — so
 * each aligning itself was one pill drawn under the other. Here they are a column, and
 * whichever is live is the one that shows.
 *
 * **In `app/shell` because it is the only layer that may name both.** `core/` cannot
 * import a feature, so the editor's banner is the editor's; wiring the two together is
 * what "app wires" means in the layout.
 */
@Composable
fun StatusBanners() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FrameExportBanner()
            EditorSaveBanner()
            EditorNoticeBanner()
            UnlinkUndoBanner()
        }
    }
}
