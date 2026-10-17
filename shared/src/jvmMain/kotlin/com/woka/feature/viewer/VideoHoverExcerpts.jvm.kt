package com.woka.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.woka.core.data.StillColorInfo
import com.woka.feature.viewer.shell.rememberFramePainter
import com.woka.feature.viewer.video.engine.preview.hoverExcerpts
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** How long the pointer rests before anything is decoded (SIDE-006). */
private const val HOVER_DELAY_MS = 400L

private const val SEGMENTS = 5

@Composable
actual fun VideoHoverExcerpts(file: File, modifier: Modifier) {
    var frame by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    var color by remember(file) { mutableStateOf(StillColorInfo.Sdr) }
    var segment by remember(file) { mutableIntStateOf(0) }
    var progress by remember(file) { mutableFloatStateOf(0f) }

    // Collected on the composition's thread, so every write below is a main-thread
    // write; the decoding happens upstream, on the reel's own thread.
    LaunchedEffect(file) {
        delay(HOVER_DELAY_MS.milliseconds)
        hoverExcerpts(file).collect { excerpt ->
            color = excerpt.color
            segment = excerpt.index
            excerpt.frames.forEachIndexed { i, next ->
                frame = next
                progress = (i + 1f) / excerpt.frames.size
                delay(excerpt.frameMs.milliseconds)
            }
        }
    }

    val shown = frame ?: return
    Box(modifier = modifier) {
        Image(
            painter = rememberFramePainter(shown, color, moving = true),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        ExcerptBar(segment, progress, Modifier.align(Alignment.TopCenter))
    }
}

/** `.segs`: five bars, the past ones full, the current one filling, the rest empty. */
@Composable
private fun ExcerptBar(segment: Int, progress: Float, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(SEGMENTS) { index ->
            val fill = when {
                index < segment -> 1f
                index == segment -> progress
                else -> 0f
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Color.White))
            }
        }
    }
}
