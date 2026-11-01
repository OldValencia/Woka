package com.woka.support

import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * How much an RGBA [frame] combs: the mean of `|p(y) − (p(y−1)+p(y+1))/2|` over
 * odd rows and even columns, on the G channel. A woven interlaced frame in motion
 * scores high, a deinterlaced one low — `interlaced.ts` frame 12: 2.48 raw, 0.92
 * through `bwdif` (VF-102, feature doc §7).
 */
fun combScore(frame: Frame): Double {
    val buffer = frame.image[0] as ByteBuffer
    val stride = frame.imageStride
    fun at(x: Int, y: Int) = buffer.get(y * stride + x * 4 + 1).toInt() and 0xFF
    var sum = 0.0
    var count = 0
    for (y in 1 until frame.imageHeight - 1 step 2) {
        for (x in 0 until frame.imageWidth step 2) {
            sum += abs(at(x, y) - (at(x, y - 1) + at(x, y + 1)) / 2.0)
            count++
        }
    }
    return sum / count
}
