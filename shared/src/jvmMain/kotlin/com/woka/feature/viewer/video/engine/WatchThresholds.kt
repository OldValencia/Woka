package com.woka.feature.viewer.video.engine

import com.woka.core.data.WatchedWhen
import com.woka.feature.viewer.video.model.MediaChapter

/**
 * The three points on a video's timeline that playback resume (§4) judges a
 * position against: too early to keep, far enough in to count as watched, past
 * the last frame worth returning to.
 *
 * Split out of `PlaybackResume.kt` when the watched threshold became a setting —
 * that file is the saver and the resume lookup, these are pure arithmetic over a
 * duration, and keeping both in one file put it past the size mandate.
 */

/** Below this there is nothing worth returning to, on a file of any real length. */
private const val MIN_RESUME_MS = 10_000L

/** Within this of the end the file counts as watched. */
private const val FINISHED_TAIL_MS = 30_000L

/**
 * …but never more of a file than this. Thirty seconds is a rounding error at the
 * end of a film and half of a one-minute clip: with a flat tail, watching 30s of a
 * 60s video marked it fully watched, which is what the grid then drew.
 */
private const val FINISHED_TAIL_RATIO = 0.08f

/**
 * …and the same shape at the other end: ten seconds is nothing in a film and half a
 * twenty-second clip. A flat floor meant a short video could not be resumed at all —
 * it was stored, drawn on the card, and then ignored on open, which reads as the
 * setting being broken rather than being cautious. `Always` has to mean always.
 */
private const val WATCH_FLOOR_RATIO = 0.02f

/**
 * What [WatchedWhen.MostOfIt] means, measured against a real 95:03 film whose end
 * credits start at 88:15 — 92.8 %. A share cannot *find* the credits, so it is set
 * to err late: 90 % would have marked that film watched three minutes before its
 * last scene finished, where 95 % marks it two minutes after the credits began.
 * The chapter list is the only thing that can be exact, and [creditsStartMs] uses
 * it when the file has one.
 */
private const val WATCHED_SHARE = 0.95f

/**
 * A closing title sequence has to start at least this far into the file to be
 * believed. Chapter titles are typed by whoever made the release: a `Credits`
 * chapter opening at 0 — or covering the whole film — describes something that is
 * not a film's closing credits, whatever it is called, and a wrong early mark is
 * the one failure this feature exists to avoid.
 */
private const val CREDITS_FLOOR_SHARE = 0.6f

private val CREDITS_WORDS = listOf("credit", "outro", "титр")

/** The point past which a file counts as fully played out. Pure, so it can be tested. */
internal fun finishedFromMs(durationMs: Long): Long {
    if (durationMs <= 0L) return Long.MAX_VALUE
    val tail = minOf(FINISHED_TAIL_MS, (durationMs * FINISHED_TAIL_RATIO).toLong())
    return durationMs - tail
}

/**
 * The point past which a position is worth keeping — and returning to. One floor
 * for both: "far enough in to be worth storing" and "far enough in to be worth
 * seeking back to" are the same judgement, and splitting them is what left a short
 * video with a progress bar it would never honour.
 */
internal fun watchFloorMs(durationMs: Long): Long =
    minOf(MIN_RESUME_MS, (durationMs * WATCH_FLOOR_RATIO).toLong())

/**
 * The point past which the file is marked watched (§4).
 *
 * [creditsStartMs] can only move the mark **earlier**, never later: it is extra
 * precision on top of the share, not a replacement for it, and a container that
 * starts its credits at 98 % must not make `MostOfIt` stricter than the share
 * promises. The [finishedFromMs] term is load-bearing at 95 %, not belt-and-braces:
 * 95 % of a one-minute clip is 0:57, past the 0:55.2 the tail ratio puts it at, so
 * without the clamp the default would be stricter than [WatchedWhen.TheVeryEnd] on
 * short files.
 */
internal fun watchedFromMs(durationMs: Long, mode: WatchedWhen, creditsStartMs: Long?): Long {
    val playedOut = finishedFromMs(durationMs)
    if (durationMs <= 0L || mode == WatchedWhen.TheVeryEnd) return playedOut
    val share = (durationMs * WATCHED_SHARE).toLong()
    return minOf(share, creditsStartMs ?: share, playedOut)
}

/**
 * Where the container says its closing credits start, or null when it says nothing
 * this can be trusted about — which drops the caller back on the flat share.
 *
 * The **last** chapter only, so a series that opens with its titles (chapter 1
 * `Credits`, chapter 2 the episode — a real layout) is never even looked at, and
 * only past [CREDITS_FLOOR_SHARE]. A file with fewer than two chapters cannot
 * reach this: `probeChapters` returns empty for those, since one chapter spanning
 * a whole file is not a segmentation.
 */
internal fun creditsStartMs(chapters: List<MediaChapter>, durationMs: Long): Long? {
    if (durationMs <= 0L || chapters.size < 2) return null
    val last = chapters.last()
    if (!last.title.namesCredits()) return null
    return last.startMs.takeIf { it >= (durationMs * CREDITS_FLOOR_SHARE).toLong() }
}

private fun String.namesCredits(): Boolean {
    val title = lowercase()
    return CREDITS_WORDS.any { title.contains(it) }
}
