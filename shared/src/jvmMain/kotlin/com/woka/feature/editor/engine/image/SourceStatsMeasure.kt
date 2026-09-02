package com.woka.feature.editor.engine.image

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.LUMA_BUCKETS
import com.woka.feature.editor.core.SourceStats
import kotlinx.coroutines.withContext

/** Rec. 709, the same weights the saturation matrix and the tone shader already use. */
private const val R_WEIGHT = 0.2126f
private const val G_WEIGHT = 0.7152f
private const val B_WEIGHT = 0.0722f

/**
 * The picture reduced to the four numbers a tool is allowed to see (ME-166).
 *
 * **Measured off the proxy, on purpose.** A histogram of a 2560 px downscale is the
 * histogram of the photograph — every bucket is a proportion, and a correction computed
 * from proportions is the same correction at any size. The native bitmap would be 384 MB
 * for the 100 MP source ME-010 measured, to answer a question the proxy already answers.
 *
 * **Nothing calls this on the main thread.** It reads every pixel of a ≤2560 px bitmap,
 * which is a few tens of milliseconds — small, and still a hitch if it lands in a frame.
 * `ImageEditorEngine` runs it on `ImageBitmapCache.ioDispatcher` when the proxy arrives,
 * so by the time any tool asks, the answer is already there.
 */
internal fun sourceStatsOf(proxy: ImageBitmap): SourceStats {
    val pixels = proxy.toPixelMap()
    val buckets = IntArray(LUMA_BUCKETS)
    var total = 0L

    for (y in 0 until proxy.height) {
        for (x in 0 until proxy.width) {
            val colour = pixels[x, y]
            val luma = R_WEIGHT * colour.red + G_WEIGHT * colour.green + B_WEIGHT * colour.blue
            val bucket = (luma * (LUMA_BUCKETS - 1)).toInt().coerceIn(0, LUMA_BUCKETS - 1)
            buckets[bucket]++
            total += bucket
        }
    }

    val counted = proxy.width.toLong() * proxy.height
    return SourceStats(
        luma = buckets.toList(),
        blackPoint = levelAt(buckets, counted, SourceStats.BLACK_PERCENTILE),
        whitePoint = levelAt(buckets, counted, SourceStats.WHITE_PERCENTILE),
        meanLuma = if (counted > 0) total.toFloat() / counted / (LUMA_BUCKETS - 1) else 0f
    )
}

/**
 * The level at which the running count crosses [fraction] of the picture.
 *
 * Walks the buckets rather than sorting: the histogram *is* the sorted picture, and a
 * percentile of it is one pass. Returns the last level when the fraction is never
 * reached, which happens only for an empty bitmap.
 */
private fun levelAt(buckets: IntArray, counted: Long, fraction: Float): Float {
    if (counted <= 0) return 0f
    val target = counted * fraction
    var running = 0L
    buckets.forEachIndexed { level, count ->
        running += count
        if (running >= target) return level.toFloat() / (LUMA_BUCKETS - 1)
    }
    return 1f
}

/**
 * Holds the one measurement an opened picture ever needs (ME-166).
 *
 * **Measured once.** [measureOnce] is handed every bitmap the canvas draws — the proxy,
 * then the native frame if ME-116 swaps it in at zoom — and keeps the first. A histogram
 * is a set of proportions, so the two would answer the same question at different cost,
 * and re-measuring on every zoom would spend that cost repeatedly for no new answer.
 *
 * A `mutableStateOf` rather than a plain field because the shell reads it during
 * composition: a tool asks through `EditorTool.resolve`, and a panel built before the
 * proxy landed has to recompose once it has.
 */
internal class SourceStatsHolder {

    var stats: SourceStats? by mutableStateOf(null)
        private set

    suspend fun measureOnce(proxy: ImageBitmap) {
        if (stats != null) return
        // Off the main thread: this reads every pixel of a ≤2560 px bitmap, which is
        // small and still a hitch if it lands inside a frame.
        stats = withContext(ImageBitmapCache.ioDispatcher) { sourceStatsOf(proxy) }
    }
}
