package com.woka.feature.viewer.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.TransferKind
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * What every HDR source was assumed to peak at until ME-120: 1120 nits.
 *
 * **Kept, and only as the answer for a picture nobody has measured** — a video frame,
 * whose bitmap changes at the frame rate. It is a guess and always was; what it is not,
 * any more, is the divisor for a still whose pixels are right there to look at.
 */
internal const val ASSUMED_WHITE_POINT = 11.2f

/** SMPTE ST 2084, in the constants `HDR_SHADER` states — the two must not drift. */
private const val M1 = 0.1593017578125f
private const val M2 = 78.84375f
private const val C1 = 0.8359375f
private const val C2 = 18.8515625f
private const val C3 = 18.6875f

/** PQ is absolute: full scale is 10000 nits, against a 100-nit reference. */
private const val PQ_RANGE = 100f

/** ARIB STD-B67's nominal peak over the same reference, after the OOTF's gamma. */
private const val HLG_RANGE = 10f
private const val HLG_A = 0.17883277f
private const val HLG_B = 0.28466892f
private const val HLG_C = 0.55991073f
private const val HLG_GAMMA = 1.2f

/**
 * **A percentile, not the maximum** — one hot pixel or one encoder artefact is not the
 * brightness of a photograph. But only just below it: MaxCLL, the metadata this stands in
 * for, *is* the maximum, and a white point under the picture's real peak clips the
 * highlights the curve exists to preserve.
 *
 * Measured on `hdr_real_pq.avif`, whose peak is 2.94 units: the 99.5th percentile lands at
 * **1.02** and the 99.9th at **1.88**. The first throws away most of the highlight range —
 * everything above SDR white would clip — which is why the number here is the second.
 */
private const val PERCENTILE = 0.999f

/**
 * **The histogram is over the signal, not over linear light**, and that is the whole
 * reason it can be this small. Light is what the answer is in, but 1024 linear buckets
 * across 0…100 × SDR white are 0.098 units apart — coarser, near 1.0, than the difference
 * between compressing a picture and leaving it alone. PQ and HLG are perceptual curves, so
 * bucketing the code they arrive in spends the resolution where the tones are; 1024 is
 * exactly the precision 10-bit content has to offer. The percentile's *code* is converted
 * once, at the end.
 */
private const val BUCKETS = 1024
private const val MAX_UNITS = 100f

/**
 * **About this many samples, whatever the picture measures.** A 100 MP still and a
 * 640 × 480 one cost the same, because the stride is derived from the pixel count rather
 * than fixed — which is what keeps this inside a `remember` instead of on a thread.
 */
private const val TARGET_SAMPLES = 40_000

/**
 * How bright this picture actually gets, in the units `HDR_SHADER` reasons in — SDR
 * reference white is 1.0 (ME-120).
 *
 * **Why measure at all.** The obvious source is mastering-display or MaxCLL metadata, and
 * nothing in this repository's corpus carries either — checked 2026-09-03. A
 * metadata-first design would fall through to [ASSUMED_WHITE_POINT] on every file that
 * can be tested, which is the defect rather than the fix.
 *
 * **Never below 1.0.** A picture that does not exceed SDR white needs no compression, and
 * a white point under 1.0 would make `hable(x) / hable(W)` *expand* it — the same defect
 * as today, pointing the other way.
 */
internal fun measuredWhitePoint(bitmap: ImageBitmap, transfer: TransferKind): Float {
    if (transfer == TransferKind.SDR) return 1f

    val pixels = bitmap.toPixelMap()
    val stride = strideFor(bitmap.width, bitmap.height)
    val buckets = IntArray(BUCKETS)
    var counted = 0

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val colour = pixels[x, y]
            // The brightest channel, not a luma: the curve is applied per channel, so what
            // has to fit under the white point is whichever one reaches highest.
            val signal = max(max(colour.red, colour.green), colour.blue)
            val bucket = (signal * (BUCKETS - 1)).toInt().coerceIn(0, BUCKETS - 1)
            buckets[bucket]++
            counted++
            x += stride
        }
        y += stride
    }

    val code = percentileOf(buckets, counted)
    return max(1f, linearUnitsOf(code, transfer))
}

/** One sample every *n* pixels on both axes, so the count is bounded by area. */
private fun strideFor(width: Int, height: Int): Int {
    val area = width.toLong() * height
    if (area <= TARGET_SAMPLES) return 1
    var stride = 1
    while (area / (stride.toLong() * stride) > TARGET_SAMPLES) stride++
    return stride
}

/** The signal code at which the running count crosses [PERCENTILE] of what was sampled. */
private fun percentileOf(buckets: IntArray, counted: Int): Float {
    if (counted <= 0) return 0f
    val target = counted * PERCENTILE
    var running = 0
    buckets.forEachIndexed { bucket, count ->
        running += count
        if (running >= target) return bucket.toFloat() / (BUCKETS - 1)
    }
    return 1f
}

/**
 * One channel's signal carried into linear light, by the same two curves the shader
 * applies — written twice on purpose and marked so, because a divisor derived from a
 * different transfer than the one being divided is a defect nothing would report.
 */
private fun linearUnitsOf(signal: Float, transfer: TransferKind): Float = when (transfer) {
    TransferKind.PQ -> {
        val p = signal.coerceIn(0f, 1f).pow(1f / M2)
        val num = max(p - C1, 0f)
        val den = C2 - C3 * p
        if (den <= 0f) MAX_UNITS else (num / den).pow(1f / M1) * PQ_RANGE
    }
    TransferKind.HLG -> {
        val e = signal.coerceIn(0f, 1f)
        val scene = if (e <= 0.5f) (e * e) / 3f else (exp((e - HLG_C) / HLG_A) + HLG_B) / 12f
        scene.pow(HLG_GAMMA) * HLG_RANGE
    }
    TransferKind.SDR -> 1f
}
