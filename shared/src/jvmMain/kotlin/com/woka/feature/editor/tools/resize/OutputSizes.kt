package com.woka.feature.editor.tools.resize

import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The size row's arithmetic (RD-401), with no Compose: the presets a photo and a film offer,
 * the renderer's limits, and the other side of a field kept in proportion.
 */

/** One button of the size row: its key, its word, the pixels it fills the fields with, what it saves. */
data class SizePreset(val key: String, val label: String, val size: IntSize, val choice: SizeChoice)

/** The row's last button, which names no size of its own. */
const val CUSTOM_KEY = "custom"

/** The key `Original` answers to on every row. */
const val ORIGINAL_KEY = "100"

/** A photo's row: `Original`, then 75 / 50 / 25 % of [frame] — the frame after crop, before any Resize. */
fun photoSizePresets(frame: IntSize): List<SizePreset> =
    listOf(SizePreset(ORIGINAL_KEY, "Original", frame, SizeChoice.Original)) +
        listOf(75f, 50f, 25f).map { percent ->
            val key = percent.roundToInt().toString()
            SizePreset(key, "$key%", resizedFrame(percent, frame), SizeChoice.Percent(percent))
        }

/** The standard heights a film's presets are drawn from, largest first. */
private val TIERS = listOf(2160, 1440, 1080, 720, 480)

/**
 * A film's row (`dialogs.js` `videoSizePresets`): `Original` at [frame], then the two largest
 * tiers strictly smaller than it. Each tier's 16:9 bounds — swapped for a portrait frame — are
 * fitted at the frame's proportions, rounded to even sides, never grown past the frame, and
 * dropped when they repeat a size already offered. `720p` names a size only when it is the
 * tier's own; otherwise the button reads its pixels.
 */
fun videoSizePresets(frame: IntSize): List<SizePreset> {
    val presets = mutableListOf(SizePreset(ORIGINAL_KEY, "Original", frame, SizeChoice.Original))
    if (frame.width <= 0 || frame.height <= 0) return presets
    val portrait = frame.height > frame.width
    for (tier in TIERS) {
        val wide = IntSize(2 * (tier * 16 / 18.0).roundToInt(), tier)
        val bounds = if (portrait) IntSize(wide.height, wide.width) else wide
        val scale = min(bounds.width / frame.width.toDouble(), bounds.height / frame.height.toDouble())
        if (scale >= 1.0) continue
        val size = IntSize(fitSide(frame.width, scale, bounds.width), fitSide(frame.height, scale, bounds.height))
        if (size.width < 2 || size.height < 2 || presets.any { it.size == size }) continue
        val label = if (size == bounds) "${tier}p" else "${size.width} × ${size.height}"
        presets += SizePreset(tier.toString(), label, size, SizeChoice.Custom(size.width, size.height, true))
        if (presets.size == 3) break
    }
    return presets
}

private fun fitSide(side: Int, scale: Double, bound: Int): Int =
    minOf(2 * (side * scale / 2).roundToInt(), 2 * (side / 2), bound)

/** What a renderer accepts in `Custom` (the author, 2026-11-27), and what it says otherwise. */
enum class SizeLimits(val error: String) {
    /** 1–16384 px a side; enlarging is allowed. */
    Photo("Use whole pixels from 1 to 16384."),

    /** H.264: even sides from 2, the long one up to 4096 and the short up to 2304. */
    Video("Use even sizes from 2 up to 4096 × 2304.");

    fun accepts(size: IntSize): Boolean = when (this) {
        Photo -> size.width in 1..MAX_PHOTO_SIDE && size.height in 1..MAX_PHOTO_SIDE
        Video -> size.width % 2 == 0 && size.height % 2 == 0 && min(size.width, size.height) >= 2 &&
            max(size.width, size.height) <= MAX_VIDEO_LONG && min(size.width, size.height) <= MAX_VIDEO_SHORT
    }

    /** The other side for [typed] pixels on one axis, in [frame]'s proportions — even for a film. */
    fun linked(typed: Int, typedSide: Int, otherSide: Int): Int {
        val exact = typed.toDouble() * otherSide / typedSide.coerceAtLeast(1)
        return if (this == Video) max(2, 2 * (exact / 2).roundToInt()) else max(1, exact.roundToInt())
    }
}

private const val MAX_VIDEO_LONG = 4096
private const val MAX_VIDEO_SHORT = 2304
