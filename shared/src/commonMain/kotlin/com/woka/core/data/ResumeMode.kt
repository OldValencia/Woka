package com.woka.core.data

/**
 * Playback resume (§4). Three modes rather than a flag plus a threshold: "off" and
 * "long videos only" are not two states of one switch, and folding the third into
 * a sentinel threshold would be unreadable in `settings.json`.
 *
 * [LongOnly] compares against `resumeMinDurationMinutes` — a film or an episode is
 * worth resuming, a clip is not, and being asked to resume a two-minute video is
 * exactly what makes the feature annoying.
 */
enum class ResumeMode { Off, LongOnly, Always }

/**
 * Where "watched" begins (§4). Split from the resume modes above rather than added
 * to them: resume answers *whether a file is tracked at all*, this answers *how much
 * of a tracked file has to be seen*, and a five-value enum crossing the two would
 * have to spell out combinations nobody asked for.
 *
 * [TheVeryEnd] is the behaviour that shipped with the watched markers — the last
 * half-minute of a film. It turned out to record a film watched to its credits as
 * unfinished, because a closing title sequence is minutes long and stopping in it
 * never reaches that tail.
 *
 * [MostOfIt] is the default: the mark exists to say "you have seen this", and a
 * film abandoned over its credits has been seen.
 */
enum class WatchedWhen { MostOfIt, TheVeryEnd }

/**
 * Whether a video of this length participates in resume (§4). A duration of 0 —
 * not yet known, or not a video — never does: without it "long enough" has no
 * meaning and the position could not be judged finished either.
 */
fun WokaSettings.resumeAppliesTo(durationMs: Long): Boolean = when (resumeMode) {
    ResumeMode.Off -> false
    ResumeMode.Always -> durationMs > 0L
    ResumeMode.LongOnly -> durationMs >= resumeMinDurationMinutes * 60_000L
}
