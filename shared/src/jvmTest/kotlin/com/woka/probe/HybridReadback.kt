package com.woka.probe

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * TEMPORARY (ME-295): independent FFmpeg hashes catch green/concealed frames and lost tails.
 * Bundled JavaCV is checked too, including a fresh seek into both sides of each join.
 * All hashes use RGB24 so a decoder's changed pixel-format announcement cannot hide mismatch.
 */
internal fun hybridCheck(segments: List<File>, output: File): String {
    val expected = segments.flatMap { hybridHashes(it) }
    val actual = hybridHashes(output)
    val mismatches = (0 until minOf(expected.size, actual.size)).count { expected[it] != actual[it] }
    val bundle = readBack(output)
    val expectedSeek = segments.flatMap { part ->
        withGrabber(part) { grabber ->
            buildList {
                while (true) add(hybridPixels(grabber.grabImage() ?: break))
            }
        }
    }
    val seek = listOf(1_900L, 2_000L, 2_100L, 3_900L, 4_000L, 4_100L).map { ms ->
        runCatching {
            withGrabber(output) { grabber: FFmpegFrameGrabber ->
                grabber.setTimestamp(ms * 1000)
                val frame = grabber.grabImage()
                val index = (grabber.timestamp * 30.0 / 1_000_000).roundToInt()
                val matches = frame != null && abs(grabber.timestamp / 1000 - ms) <= 34 &&
                    hybridPixels(frame) == expectedSeek.getOrNull(index)
                "$ms:${grabber.timestamp / 1000}:${if (matches) "ok" else "BAD"}"
            }
        }.getOrElse { "$ms:FAIL" }
    }
    return "expected=${expected.size} decoded=${actual.size} wrong=$mismatches bundled=[$bundle] seek=$seek bytes=${output.length()}"
}

/** Ignore row padding; only displayed BGR pixels enter the independent seek comparison. */
private fun hybridPixels(frame: Frame): Long {
    val crc = CRC32()
    val pixels = (frame.image[0] as ByteBuffer).duplicate()
    val row = ByteArray(frame.imageWidth * frame.imageChannels)
    repeat(frame.imageHeight) { line ->
        pixels.position(line * frame.imageStride)
        pixels.get(row)
        crc.update(row)
    }
    return crc.value
}

internal fun hybridHashes(file: File): List<String> {
    val hashes = File(file.parentFile, "${file.name}.framemd5")
    hybridCommand(file.parentFile, "${file.name}-hash", "-v", "warning", "-i", file.path,
        "-map", "0:v:0", "-an", "-pix_fmt", "rgb24", "-fps_mode", "passthrough", "-f", "framemd5", hashes.path)
    return hashes.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.substringAfterLast(',').trim() }
}
