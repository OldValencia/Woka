package com.woka.probe

import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test

/**
 * TEMPORARY (ME-236): actual named NVDEC decoder versus the player's four-thread decoder.
 * Each mode/width runs in a fresh test JVM. Reports frames, seek cost, host/GPU memory and
 * concurrent throughput; `hwaccel=auto` is deliberately not treated as proof of GPU use.
 * Set `WOKA_HW_MODE` to `sw`, `cuvid`, `cuda-native` or `d3d11-native`;
 * `WOKA_HW_WIDTH` is `0` or `1920`. `WOKA_HW_FILE` optionally replaces S02E01.
 * Run `:shared:jvmTest --rerun --tests "*HardwareLaneProbe" --info` per mode/width.
 */
class HardwareLaneProbe {
    private val mode = System.getenv("WOKA_HW_MODE") ?: "sw"
    private val width = System.getenv("WOKA_HW_WIDTH")?.toIntOrNull() ?: 0
    private val file = File(System.getenv("WOKA_HW_FILE") ?: "D:/Movies/Avatar The Last Airbender Season 2/S02E01.mkv")
    private val lines = mutableListOf<String>()
    private val dir = File("build/me236/$mode-$width").absoluteFile.also { it.mkdirs() }

    @Test
    fun `measure actual hardware lane preparation`() {
        val pool = mutableListOf<FFmpegFrameGrabber>()
        try {
            report("mode=$mode width=$width file=$file baselineHostMB=${hostMb()} gpu=${gpuMb()}")
            val first = openHardwareLane(file, mode, width).also { pool += it }
            report("decoder=${hardwareCodec(first)} size=${first.imageWidth}x${first.imageHeight} fps=${first.frameRate}")
            for ((index, at) in listOf(0.13, 0.32, 0.51, 0.67, 0.86).withIndex()) {
                val target = (first.lengthInTime * at).toLong()
                val began = System.nanoTime()
                first.setTimestamp(target)
                val frame = first.grabImage() ?: error("No image at $at")
                val elapsed = hardwareMs(began)
                val firstUs = first.timestamp
                val held = frame.clone()
                val metadata = hardwareMetadata(first)
                repeat(2) { first.grabImage()?.clone()?.close() ?: error("Short pre-roll") }
                val prepared = hardwareMs(began)
                try {
                    File(dir, "frame$index.txt").writeText(hardwareSnapshot(held, firstUs))
                } finally {
                    held.close()
                }
                report("solo at=$at seekFirstMs=$elapsed seekThreeMs=$prepared landedUs=${first.timestamp} targetUs=$target")
                report("metadata at=$at $metadata")
            }
            report("pool=1 hostMB=${hostMb()} gpu=${gpuMb()}")
            for (count in 2..4) {
                if (hostMb() > 1800) {
                    report("STOP pool growth at ${count - 1}: host ceiling 1800 MB (ME-236 probe budget)")
                    break
                }
                val began = System.nanoTime()
                val next = openHardwareLane(file, mode, width).also { pool += it }
                val opened = hardwareMs(began)
                next.setTimestamp((next.lengthInTime * (0.1 + count * 0.13)).toLong())
                repeat(3) { next.grabImage()?.clone()?.close() ?: error("Short pool pre-roll") }
                report("pool=$count openMs=$opened readyMs=${hardwareMs(began)} hostMB=${hostMb()} gpu=${gpuMb()}")
            }
            if (pool.size >= 2) underPlay(pool[0], pool[1])
            val rates = DoubleArray(pool.size)
            val failures = arrayOfNulls<String>(pool.size)
            val start = CountDownLatch(1)
            val workers = pool.mapIndexed { index, grabber ->
                thread {
                    start.await()
                    runCatching {
                        val began = System.nanoTime()
                        var frames = 0
                        repeat(120) { if (grabber.grabImage() != null) frames++ }
                        rates[index] = frames * 1000.0 / hardwareMs(began).coerceAtLeast(1)
                    }.onFailure { failures[index] = it.message }
                }
            }
            start.countDown()
            workers.forEach { it.join() }
            report("concurrent=${pool.size} fps=${rates.joinToString { "%.1f".format(it) }} errors=${failures.toList()} hostMB=${hostMb()} gpu=${gpuMb()}")
        } catch (failure: Throwable) {
            report("FAILED ${failure.javaClass.simpleName}: ${failure.message}")
        } finally {
            pool.forEach { grabber ->
                runCatching { grabber.stop() }
                runCatching { grabber.release() }
            }
            report("closed hostMB=${hostMb()} gpu=${gpuMb()}")
            File(dir, "results.txt").writeText(lines.joinToString("\n"))
        }
    }

    private fun underPlay(a: FFmpegFrameGrabber, b: FFmpegFrameGrabber) {
        val playing = AtomicBoolean(true)
        val decoded = AtomicInteger()
        val ready = CountDownLatch(1)
        var failure: String? = null
        val worker = thread {
            try {
                while (playing.get() && a.grabImage() != null) {
                    decoded.incrementAndGet()
                    ready.countDown()
                }
            } catch (problem: Throwable) {
                failure = problem.message
            } finally {
                ready.countDown()
            }
        }
        ready.await()
        try {
            for (at in listOf(0.13, 0.32, 0.51, 0.67, 0.86)) {
                val before = decoded.get()
                val began = System.nanoTime()
                b.setTimestamp((b.lengthInTime * at).toLong())
                repeat(3) { b.grabImage()?.clone()?.close() ?: error("No concurrent image") }
                val elapsed = hardwareMs(began).coerceAtLeast(1)
                report("underPlay at=$at seekThreeMs=$elapsed aFps=${"%.1f".format((decoded.get() - before) * 1000.0 / elapsed)}")
            }
        } finally {
            playing.set(false)
            worker.join()
            report("underPlay failure=$failure")
        }
    }

    private fun report(line: String) {
        lines += line
        println("ME236 $line")
    }

    private fun hostMb(): Long = Pointer.physicalBytes() / (1024 * 1024)
}
