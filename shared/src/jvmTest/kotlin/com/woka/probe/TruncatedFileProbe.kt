package com.woka.probe

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.support.onPlayer
import com.woka.support.startKoinOnce
import com.woka.support.waitFor
import java.io.File
import kotlin.test.Test

/** A folder of cut or damaged videos — not committed, so pointed at rather than shipped. */
private const val SAMPLES_ENV = "WOKA_TRUNCATED_SAMPLES"

/** The repository's hand-check samples, every one cut mid-stream (`samples/deinterlace/README.md`). */
private const val CUT_SAMPLES = "../samples/deinterlace"

/** How long past its stated length a file may take to reach the end before it counts as stuck. */
private const val GRACE_MS = 8_000L

/**
 * VF-103 step 1: what the player does with a file that stops mid-write. Run by hand:
 *
 *     $env:WOKA_TRUNCATED_SAMPLES = "D:\cut"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.TruncatedFileProbe" -i
 *
 * Per file: the stated length, whether the probe latch completes, how many pictures
 * reach the screen, where the play head stops, whether the end is raised and how long
 * after opening, and whether playback is still "playing" with nothing moving. Asserts
 * nothing.
 */
class TruncatedFileProbe {

    @Test
    fun `what the player does with a file cut mid-write`() {
        startKoinOnce()
        val media = File(javaClass.getResource("/media/README.md")!!.toURI()).parentFile
        val fixtures = listOf("dahua.dav", "broken.mkv").map { File(media, it) }
        val cut = File(CUT_SAMPLES).listFiles().orEmpty().filter { it.extension != "md" }
        val extra = System.getenv(SAMPLES_ENV)?.takeIf { it.isNotBlank() }?.let(::File)?.listFiles().orEmpty().toList()
        println("file | length ms | probed | pictures | head ms | end raised after ms | playing")
        (fixtures + cut + extra).sortedBy { it.name }.forEach { println(row(it)) }
    }

    private fun row(file: File): String = runCatching {
        onPlayer(file) { player -> measure(file, player) }
    }.getOrElse { "${file.name} | FAILED: ${it::class.simpleName}: ${it.message?.take(80)}" }

    private fun measure(file: File, player: VideoPlayerState): String {
        val opened = System.currentTimeMillis()
        waitFor(10_000L) { player.tracksProbed.isCompleted && player.durationMs > 0L }
        val length = player.durationMs
        var pictures = 0
        var lastId = -1L
        var lastFrame = player.currentFrame
        val deadline = opened + length + GRACE_MS
        while (System.currentTimeMillis() < deadline && !player.reachedEnd) {
            val frame = player.currentFrame
            if (frame != null && (frame !== lastFrame || player.currentFrameId != lastId)) pictures++
            lastFrame = frame
            lastId = player.currentFrameId
            Thread.sleep(5L)
        }
        val endAfter = if (player.reachedEnd) "${System.currentTimeMillis() - opened}" else "never"
        return "${file.name} | $length | ${player.tracksProbed.isCompleted} | $pictures | " +
            "${player.currentPositionMs} | $endAfter | ${player.isPlaying}"
    }
}
