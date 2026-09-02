package com.woka.feature.editor.core

/** A histogram is 256 buckets, one per 8-bit level, whatever depth the source had. */
const val LUMA_BUCKETS = 256

/**
 * What a tool may know about the **picture** rather than about its parameters (ME-166).
 *
 * **Numbers, never pixels.** `EditorTool` lives in `commonMain` precisely so it cannot
 * name a raster type (§3.3), and that is the constraint this type exists to respect: an
 * automatic correction needs a histogram and a black point, not a bitmap. Handing over
 * the pixels would let a tool sample them inside `stage()`, and `stage()` has to stay a
 * pure function of parameters or the same fold could not run at preview scale and again
 * at scale 1.0.
 *
 * **A `List<Int>`, not an `IntArray`.** State in this app is immutable by mandate, and an
 * array in a `data class` is neither immutable nor correctly equal. 256 boxed ints, built
 * once per opened picture, is not a cost worth an exception.
 *
 * Measured off the **proxy**, so [luma] sums to the proxy's pixel count and not the
 * source's. Every field below is a proportion or a level, so none of them cares.
 */
data class SourceStats(
    /** [LUMA_BUCKETS] counts of Rec. 709 luma, index 0 black to 255 white. */
    val luma: List<Int>,
    /** The level below which [BLACK_PERCENTILE] of the picture sits, as 0…1. */
    val blackPoint: Float,
    /** The level above which [WHITE_PERCENTILE] of it sits, as 0…1. */
    val whitePoint: Float,
    /** Mean luma as 0…1 — what an exposure correction is measured against. */
    val meanLuma: Float
) {
    companion object {

        /**
         * **Percentiles, not the minimum and maximum.** One hot pixel, one dead pixel or
         * a speck of dust would otherwise decide the exposure of a whole photograph, and
         * a single-pixel specular highlight is present in most of them.
         */
        const val BLACK_PERCENTILE = 0.001f
        const val WHITE_PERCENTILE = 0.999f
    }
}
