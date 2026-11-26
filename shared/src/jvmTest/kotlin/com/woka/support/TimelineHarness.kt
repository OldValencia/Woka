package com.woka.support

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.engine.video.VideoEditorTimeline
import com.woka.feature.editor.engine.video.VideoPlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The docked timeline (RD-301) over a real 3 s film and a [FixtureTimeline] seeded with
 * [document] — the setup every timeline UI test shares.
 *
 * The holder is opened the way the canvas opens it — paused, at no volume — and closed in
 * `finally`, so a failing assertion cannot leave a decoder running into the next test. The
 * timeline stands 140 dp down its window, where the canvas would be above it, so the skim
 * badge it floats over that space has room to be drawn.
 */
@OptIn(ExperimentalTestApi::class)
internal fun onTimeline(
    /** Read in composition, so a test can change it mid-gesture. */
    width: () -> Dp = { 620.dp },
    document: EditDocument = EditDocument(),
    /** Drawn beside the timeline on the same holder — the Clip panel (RD-302). */
    panel: (@Composable (VideoPlayerHolder, FixtureTimeline) -> Unit)? = null,
    block: ComposeUiTest.(VideoPlayerHolder, FixtureTimeline) -> Unit
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val holder = VideoPlayerHolder()
    try {
        holder.open(scope, episodeTwoFile(), volume = 0f)
        awaitTrue("a duration to be read", timeoutMs = 20_000L) { (holder.state?.durationMs ?: 0L) > 0L }
        val edits = FixtureTimeline().also { it.load(document) }
        onUi {
            setContent {
                Row {
                    Box(Modifier.padding(top = 140.dp).width(width())) {
                        VideoEditorTimeline(holder, edits.document, edits, Modifier)
                    }
                    panel?.let { Box(Modifier.width(280.dp)) { it(holder, edits) } }
                }
            }
            waitForIdle()
            block(holder, edits)
        }
    } finally {
        holder.close()
        scope.cancel()
    }
}
