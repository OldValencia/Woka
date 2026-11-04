package com.woka.probe

import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import kotlin.concurrent.thread
import kotlin.test.Test

/** The pool sizes measured: two is ME-232 as written, the rest is short clips. */
private const val MAX_DECODERS = 4

/** Where the pool stops growing — under the test JVM's 2 GB `maxPhysicalBytes`. */
private const val RESIDENT_CEILING_MB = 1500

/** Decoder threads, from the environment so each setting runs in a fresh process. */
private val threads = System.getenv("WOKA_PROBE_THREADS") ?: "auto"

/** Frames each decoder runs when the whole pool decodes at once. */
private const val POOL_RUN_FRAMES = 96

/**
 * What each decoder past the first costs — the measurement behind the pool size for
 * short clips (ME-230, `playback.md` §4).
 *
 *     $env:WOKA_PROBE_VIDEOS = "D:\a.mkv"
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.DecoderPoolProbe" -i --no-daemon
 *
 * With two decoders, the idle one can start on clip N+2 only once N+1 is playing, so a
 * clip shorter than the next one's pre-roll still stalls; covering it takes one decoder
 * per clip inside the lead. For N = 1…[MAX_DECODERS] grabbers opened as the player's
 * (`openVideoGrabber`, native size), each seeked to its own place and holding a decoded
 * frame: the process's native resident memory, every decoder's rate with the whole pool
 * decoding at once, and the last one's seek while the others run.
 *
 * Asserts nothing; prints a table.
 */
class DecoderPoolProbe {

    @Test
    fun `what each decoder costs`() {
        val files = System.getenv("WOKA_PROBE_VIDEOS").orEmpty()
            .split(';').map(String::trim).filter(String::isNotEmpty).map(::File).filter(File::isFile)
        if (files.isEmpty()) {
            println("DecoderPoolProbe: set WOKA_PROBE_VIDEOS to one or more 2160p files; nothing measured")
            return
        }
        files.forEach(::measure)
    }

    private fun measure(file: File) {
        val baseline = residentMb()
        println("PROBE ${file.name.take(28)} | threads $threads | baseline ${baseline} MB resident")
        val pool = mutableListOf<FFmpegFrameGrabber>()
        try {
            for (size in 1..MAX_DECODERS) {
                // JavaCPP refuses past `maxPhysicalBytes` (2 GB in the test JVM); stop short of it.
                if (residentMb() > RESIDENT_CEILING_MB) break
                val grabber = open(file)
                val start = System.nanoTime()
                val runners = pool.map { other -> thread { repeat(POOL_RUN_FRAMES) { other.grabImage() } } }
                grabber.setTimestamp((grabber.lengthInTime * (0.15 + 0.2 * size)).toLong())
                repeat(3) { grabber.grabImage() }
                val seekMs = ms(start)
                runners.forEach(Thread::join)
                pool += grabber
                val resident = residentMb()
                val rates = poolRates(pool)
                println(
                    "PROBE   $size decoders | resident ${resident} MB (+${resident - baseline}) | " +
                        "last seek+3 ${seekMs} ms beside ${size - 1} running | each ${rates.joinToString(" / ")} fps"
                )
            }
        } finally {
            pool.forEach {
                it.stop()
                it.release()
            }
        }
    }

    /** Every decoder in [pool] decoding [POOL_RUN_FRAMES] at once, each one's rate. */
    private fun poolRates(pool: List<FFmpegFrameGrabber>): List<String> {
        val took = LongArray(pool.size)
        val runners = pool.mapIndexed { index, grabber ->
            thread {
                val start = System.nanoTime()
                repeat(POOL_RUN_FRAMES) { grabber.grabImage() }
                took[index] = ms(start)
            }
        }
        runners.forEach(Thread::join)
        return took.map { "%.0f".format(if (it > 0) POOL_RUN_FRAMES * 1000.0 / it else 0.0) }
    }

    private fun open(file: File) = FFmpegFrameGrabber(file).apply {
        setVideoOption("threads", threads)
        pixelFormat = AV_PIX_FMT_RGBA
        audioStream = Int.MAX_VALUE
        start()
    }

    /** The process's resident set, which is where FFmpeg's buffers live — not the Java heap. */
    private fun residentMb(): Long {
        System.gc()
        return Pointer.physicalBytes() / (1024 * 1024)
    }

    private fun ms(since: Long): Long = (System.nanoTime() - since) / 1_000_000
}
