package com.woka.support

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.ui.image.IMAGE_CACHE_BUDGET_PROPERTY

/**
 * Runs [block] against a small `ImageBitmapCache` budget, restoring the previous
 * value afterwards — the same shape as [TempStorageRoot], and for the same
 * reason: the cache is a process-wide singleton, so a test must put back what it
 * found.
 *
 * The cache being a singleton also means a small budget evicts whatever other
 * tests left in it. That is harmless — every entry is re-derivable, and the
 * poster path has its PNG on disk behind it — but it is why nothing here asserts
 * on entries it did not put there itself.
 */
fun <T> withImageCacheBudget(bytes: Long, block: () -> T): T {
    val previous = System.getProperty(IMAGE_CACHE_BUDGET_PROPERTY)
    System.setProperty(IMAGE_CACHE_BUDGET_PROPERTY, bytes.toString())
    return try {
        block()
    } finally {
        if (previous == null) {
            System.clearProperty(IMAGE_CACHE_BUDGET_PROPERTY)
        } else {
            System.setProperty(IMAGE_CACHE_BUDGET_PROPERTY, previous)
        }
    }
}

/** Exactly [megabytes] MB of N32Premul pixels, so the arithmetic stays readable. */
fun bitmapOf(megabytes: Int): ImageBitmap {
    val side = 512
    require(side * side * 4 == MEGABYTE.toInt()) { "a 512x512 N32 bitmap is one megabyte" }
    return ImageBitmap(side, side * megabytes)
}

const val MEGABYTE = 1024L * 1024L
