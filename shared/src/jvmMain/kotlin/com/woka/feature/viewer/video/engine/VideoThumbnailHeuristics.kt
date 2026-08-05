package com.woka.feature.viewer.video.engine

import java.awt.image.BufferedImage
import kotlin.random.Random

/**
 * The two judgement calls behind poster-frame selection, split out of
 * [VideoThumbnailExtractor] per the §2 file-size mandate.
 */

/**
 * Where to look for a frame worth showing. Openings are usually black or a logo
 * card, so the candidates sit in the 5–25% band rather than at the start, and
 * they are randomised so re-generating a thumbnail can produce a better one.
 */
internal fun buildTimestampCandidates(duration: Long): List<Long> {
    return if (duration > 1000000L) {
        val d05 = (duration * 0.05).toLong()
        val d15 = (duration * 0.15).toLong()
        val d20 = (duration * 0.20).toLong()
        val d25 = (duration * 0.25).toLong()
        listOf(
            Random.nextLong(d05, d15.coerceAtLeast(d05 + 1)),
            Random.nextLong(d15, d20.coerceAtLeast(d15 + 1)),
            Random.nextLong(d20, d25.coerceAtLeast(d20 + 1))
        )
    } else if (duration > 0L) {
        listOf((duration / 2).coerceAtLeast(0L))
    } else {
        listOf(2000000L, 5000000L, 10000000L, 20000000L)
    }
}

/**
 * Rejects fade-ins and title cards: a frame whose sampled channels barely vary is
 * a flat colour, and makes a useless cover. Sampled on a 20×20 grid, not per
 * pixel — this runs on every candidate frame of every video in a folder.
 */
internal fun isMonochrome(image: BufferedImage): Boolean {
    if (image.width <= 0 || image.height <= 0) return true
    var minR = 255
    var maxR = 0
    var minG = 255
    var maxG = 0
    var minB = 255
    var maxB = 0
    for (y in 0 until image.height step (image.height / 20).coerceAtLeast(1)) {
        for (x in 0 until image.width step (image.width / 20).coerceAtLeast(1)) {
            val rgb = image.getRGB(x, y)
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            if (r < minR) minR = r
            if (r > maxR) maxR = r
            if (g < minG) minG = g
            if (g > maxG) maxG = g
            if (b < minB) minB = b
            if (b > maxB) maxB = b
        }
    }
    return (maxR - minR) < 18 && (maxG - minG) < 18 && (maxB - minB) < 18
}
