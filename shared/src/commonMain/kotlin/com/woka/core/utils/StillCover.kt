package com.woka.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/**
 * A grid-sized picture of a still, for the formats Coil cannot read.
 *
 * The grid draws its covers with `AsyncImage`, and Coil decodes what the
 * platform decodes — which is not HEIF, AVIF or TIFF. Those used to be filtered
 * out of the grid entirely and showed a grey tile.
 *
 * **Not** a `coil3.decode.Decoder`, which is what the plan expected. A Coil
 * decoder would have to be told the target size and honour it, or it would
 * decode a 36-megapixel HEIC to fill a 200 dp tile — the same mistake the
 * carousel made and that this returns the *already scaled, already disk-cached*
 * preview to avoid. The video cover beside it has always worked this way, for
 * the same reason.
 */
expect suspend fun loadStillCover(file: File): ImageBitmap?

/**
 * How many pictures are inside [file] — 1 for an ordinary photograph, and 1
 * again for anything that cannot be read, because a card that cannot say
 * otherwise should say the unremarkable thing.
 *
 * Cheap: every decoder counts without decoding, and the FFmpeg one caches the
 * container layout, so a card that also draws a cover pays for the open once.
 */
expect suspend fun countFrames(file: File): Int
