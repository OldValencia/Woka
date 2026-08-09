package com.woka.feature.viewer.video.engine

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.core.data.PlaybackSpeed

/** Displayed rate below this share of the declared one counts as not keeping up. */
private const val STRUGGLING_RATIO = 0.70f

/** How long that has to hold before it is reported, in whole one-second buckets. */
private const val SUSTAINED_BUCKETS = 5

/** A declared frame rate outside this range is not something to measure against. */
private val PLAUSIBLE_FPS = 1.0..120.0

/**
 * One counting bucket. A whole second, because the count inside it is compared
 * against a per-second rate — shortening it makes every machine look starved, which
 * is exactly how the notice was previewed during development and is not a setting.
 */
private const val BUCKET_MS = 1_000L

/**
 * Measures how many frames actually reach the screen, and says when that is far
 * enough under the file's own rate for long enough to be worth telling the user.
 *
 * **It measures displayed frames, not the decoder's skip counter.** The counter is
 * easier to reach — `FramePacer` already keeps one — but it answers "was a
 * conversion skipped", which happens transiently for reasons that have nothing to
 * do with a weak machine. What the notice claims is that the picture is running
 * slow, so that is what has to be measured; a message must not be able to be right
 * for the wrong reason.
 *
 * **Nothing here is Compose state except [isStruggling].** A counter written every
 * frame as `mutableStateOf` would recompose everything reading it at the frame
 * rate, which is how a free measurement turns into a real cost. The counters are
 * plain fields, [isStruggling] flips at most twice per file, and the two rates are
 * read only when the notice is actually being drawn.
 *
 * The clock is injected so the whole policy is testable without a decoder, a file
 * or a wait — the same arrangement that caught a real off-by-sentinel defect in the
 * abandoned hardware-fallback work.
 */
@Stable
class PlaybackHealth(private val nowMs: () -> Long = System::currentTimeMillis) {

    /** True once the picture has been well under rate for [SUSTAINED_BUCKETS] seconds. */
    var isStruggling by mutableStateOf(false)
        private set

    /** Only meaningful while [isStruggling]; read by the notice, not by the tree. */
    @Volatile
    var displayedFps: Int = 0
        private set

    @Volatile
    var expectedFps: Int = 0
        private set

    private var bucketStartedAtMs = -1L
    private var framesInBucket = 0
    private var lowBuckets = 0
    private var reported = false

    /**
     * Stops measuring and throws away the bucket in progress. Called for every
     * condition under which a low frame rate is expected rather than a symptom —
     * paused, seeking, reloading, or off 1x, where the player drops frames on
     * purpose and `PlayerSpeedNotice` already explains it.
     */
    fun suspendMeasuring() {
        bucketStartedAtMs = -1L
        framesInBucket = 0
        lowBuckets = 0
    }

    /**
     * One presented frame. [expected] is the file's declared rate; an implausible
     * one leaves nothing to compare against, so measurement simply stops.
     */
    fun onFramePresented(expected: Double?) {
        if (reported) return
        if (expected == null || expected !in PLAUSIBLE_FPS) {
            suspendMeasuring()
            return
        }
        val now = nowMs()
        if (bucketStartedAtMs < 0L) {
            bucketStartedAtMs = now
            framesInBucket = 1
            return
        }
        framesInBucket++
        if (now - bucketStartedAtMs < BUCKET_MS) return
        closeBucket(expected, now)
    }

    private fun closeBucket(expected: Double, now: Long) {
        val counted = framesInBucket
        bucketStartedAtMs = now
        framesInBucket = 0
        if (counted >= expected * STRUGGLING_RATIO) {
            // One good second is enough to forgive the run: a brief stutter from a
            // background job or a disk hiccup must never reach the screen as a
            // verdict about the machine.
            lowBuckets = 0
            return
        }
        lowBuckets++
        if (lowBuckets < SUSTAINED_BUCKETS) return
        displayedFps = counted
        expectedFps = expected.toInt()
        reported = true
        isStruggling = true
    }
}

/**
 * Counts the frame the presenter has just made visible.
 *
 * Above 1x the player drops frames **by design** — the media clock is held and the
 * picture pays — so measuring there would report every large file on every machine.
 * `PlayerSpeedNotice` already explains that case, and this one stays quiet for it.
 */
internal fun VideoPlayerState.recordPresentedFrame() {
    if (playbackSpeed != PlaybackSpeed.NORMAL) {
        health.suspendMeasuring()
        return
    }
    val declared = tracks.mediaTracks.video
        .firstOrNull { it.index == tracks.selectedVideoIndex }
        ?.frameRate
    health.onFramePresented(declared)
}
