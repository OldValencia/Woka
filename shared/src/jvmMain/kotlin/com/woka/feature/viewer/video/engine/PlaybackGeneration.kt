package com.woka.feature.viewer.video.engine

/**
 * The cross-thread protocol between the three playback coroutines — video decode,
 * audio decode, presenter — expressed as one object instead of six loose
 * `@Volatile` fields on [VideoPlayerState] whose rules lived only in comments.
 *
 * **The mechanism is deliberately unchanged**: the same volatile reads and writes,
 * in the same order, polled from the same places. What changes is that a caller can
 * no longer perform half of a handshake, because the halves are no longer separate
 * assignments. In particular [requestSeek] stores the target *before* bumping the
 * counter — `VideoPlayerState.seek` used to do the reverse, so a decode loop that
 * observed the new generation between the two stores would seek to the **previous**
 * target and, having already advanced its own generation, never correct itself.
 * `handleEndOfStream` got the order right; the two disagreed.
 *
 * Counters, not flags, because coalescing is the point: three slider scrubs inside
 * one decode iteration must produce one seek to the newest target, not three seeks.
 * That is also why this is not a command channel — see the plan's Phase 9 note.
 */
internal class PlaybackGeneration {

    /** Authoritative seek target. Only meaningful together with [seek]. */
    @Volatile
    var targetMs: Long = 0L
        private set

    /**
     * Bumped on every seek. Frames carry the generation they were decoded under and
     * the presenter discards stale ones, so a seek issued while playing cannot be
     * overwritten by frames already in flight.
     */
    @Volatile
    var seek: Int = 0
        private set

    /**
     * The generation the *video* loop has finished seeking to. Audio gates on it, so
     * after a scrub sound never starts before the frame has landed.
     */
    @Volatile
    var videoReady: Int = 0
        private set

    /** Bumped on every speed change, so the audio loop rebuilds its filter graph. */
    @Volatile
    var speed: Int = 0
        private set

    /**
     * Bumped whenever the audio offset changes. The audio loop re-seeks on it rather
     * than waiting: shifting the target backwards would otherwise leave the loop
     * sitting on the lead throttle in silence.
     */
    @Volatile
    var audioDelay: Int = 0
        private set

    @Volatile
    private var rebaseRequested: Boolean = false

    /** Target first, then the counter: the bump is the signal that the data is there. */
    fun requestSeek(toMs: Long) {
        targetMs = toMs
        seek++
    }

    fun requestSpeedChange() {
        speed++
    }

    fun requestAudioDelayChange() {
        audioDelay++
    }

    /** Called by the video loop once its grabber has landed on [generation]'s target. */
    fun videoSeekComplete(generation: Int) {
        videoReady = generation
    }

    /** False while the video loop still owes a seek — the audio gate. */
    val isVideoAtSeekTarget: Boolean get() = videoReady == seek

    /**
     * Asks the presenter to drop its clock baseline. The wall time spent swapping a
     * video track, or the discontinuity a speed change introduces, is not playback
     * time: billed to the incoming frames it reads as ~1s of lateness, which is under
     * the discontinuity threshold, so the presenter would take the drop-to-catch-up
     * path and freeze the picture for seconds.
     */
    fun requestPresenterRebase() {
        rebaseRequested = true
    }

    /** Consumes a pending rebase; true at most once per [requestPresenterRebase]. */
    fun consumePresenterRebase(): Boolean {
        if (!rebaseRequested) return false
        rebaseRequested = false
        return true
    }
}
