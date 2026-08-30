package com.woka.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig

/**
 * What one decoded picture costs, and whether the cache will hold it.
 *
 * Split out of `ImageBitmapCache` when ME-123's admission gate pushed that file past
 * the size mandate. It is the natural seam: everything left there is LRU mechanics,
 * and these two are the measurement and the policy that mechanics is denominated in.
 *
 * Everything reaching the cache is `N32Premul` — both `Image.makeFromEncoded` and the
 * poster's surface snapshot produce it — but the config is read rather than assumed,
 * so an accounting that silently halves is not one toolchain change away.
 *
 * **Native bytes, not heap bytes.** Skia holds the pixels outside the JVM, so `-Xmx`
 * does not bound this and the collector never feels the pressure — which is the whole
 * reason the cache counts them itself.
 */
internal val ImageBitmap.byteSize: Long
    get() {
        val bytesPerPixel = when (config) {
            ImageBitmapConfig.Alpha8 -> 1
            ImageBitmapConfig.Rgb565 -> 2
            ImageBitmapConfig.F16 -> 8
            else -> 4
        }
        return width.toLong() * height * bytesPerPixel
    }

/**
 * Whether an entry of [bytes] is worth admitting to a cache of [budgetBytes] — **half
 * the budget** (ME-123).
 *
 * An entry that alone can evict more than half the cache is the pathology: a 100 MP
 * photograph is **384 MB** of native pixels against the 256 MB default (measured,
 * ME-010), so admitting one evicts every thumbnail and poster the app holds *and still
 * leaves the cache over budget*. At the default this admits a 24 MP photograph (96 MB)
 * and refuses a 36 MP one (138 MB); a fraction rather than a constant, so raising
 * `woka.imageCacheBytes` raises this with it.
 *
 * **Refusing costs a re-decode, never a picture** — which reverses the 2026-08-17
 * reading that a huge entry "still has to be drawable". `ImageBitmapCache.set` does not
 * answer with what it stored and every caller already holds the bitmap it passed in;
 * the one reader that consults the cache first, `ViewerThumbnails.cachedFull`, falls
 * through to a preview and a full decode on a miss.
 */
internal fun cacheAdmits(bytes: Long, budgetBytes: Long): Boolean = bytes <= budgetBytes / 2
