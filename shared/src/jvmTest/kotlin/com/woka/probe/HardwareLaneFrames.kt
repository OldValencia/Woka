package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.av_frame_side_data_name
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/** TEMPORARY (ME-236): the same conversion and software scaling as a viewer decoder lane. */
internal fun openHardwareLane(file: File, mode: String, width: Int): FFmpegFrameGrabber {
    val grabber = if (mode == "cuda-native" || mode == "d3d11-native") HardwareContextGrabber(file, mode)
    else FreshFrameGrabber(file)
    grabber.setVideoOption("threads", "4")
    if (mode == "cuvid") {
        grabber.videoCodecName = "hevc_cuvid"
        grabber.setVideoOption("gpu", "0")
    }
    grabber.audioStream = Int.MAX_VALUE
    grabber.pixelFormat = AV_PIX_FMT_RGBA
    if (width > 0) {
        grabber.imageWidth = width
        grabber.imageHeight = width * 2160 / 3840
    }
    try {
        grabber.start()
        return grabber
    } catch (failure: Throwable) {
        runCatching { grabber.release() }
        throw failure
    }
}

internal fun hardwareCodec(grabber: FFmpegFrameGrabber): String {
    val type = FFmpegFrameGrabber::class.java
    val field = type.getDeclaredField("video_c")
    field.isAccessible = true
    val context = field.get(grabber) as AVCodecContext
    return context.codec().name().string
}

/** Hardware picture parity also needs the HDR information consumed after decoding. */
internal fun hardwareMetadata(grabber: FFmpegFrameGrabber): String {
    val type = FFmpegFrameGrabber::class.java
    val field = type.getDeclaredField("picture_rgb").apply { isAccessible = true }
    val picture = field.get(grabber) as? AVFrame ?: return "no AVFrame"
    val side = (0 until picture.nb_side_data()).map { index ->
        val data = picture.side_data(index)
        "${av_frame_side_data_name(data.type()).string}:${data.size()}"
    }
    val proof = (grabber as? HardwareContextGrabber)?.let { " GPUdownloads=${it.transfers}" }.orEmpty()
    return "trc=${picture.color_trc()} primaries=${picture.color_primaries()} matrix=${picture.colorspace()} side=$side$proof"
}

/**
 * Full-frame and half-frame hashes detect incomplete downloads; a small RGB sample grid
 * supports pixel-error comparisons without retaining five 33 MB native frames in memory.
 */
internal fun hardwareSnapshot(frame: Frame, atUs: Long): String {
    val buffer = (frame.image[0] as ByteBuffer).duplicate()
    val top = CRC32()
    val bottom = CRC32()
    val row = ByteArray(frame.imageWidth * 4)
    repeat(frame.imageHeight) { y ->
        buffer.position(y * frame.imageStride)
        buffer.get(row)
        if (y < frame.imageHeight / 2) top.update(row) else bottom.update(row)
    }
    val samples = buildList {
        repeat(36) { gy ->
            val y = (gy * 2 + 1) * frame.imageHeight / 72
            repeat(64) { gx ->
                val x = (gx * 2 + 1) * frame.imageWidth / 128
                repeat(3) { channel -> add(buffer.get(y * frame.imageStride + x * 4 + channel).toInt() and 255) }
            }
        }
    }
    return "$atUs ${frame.imageWidth} ${frame.imageHeight} ${top.value} ${bottom.value}\n${samples.joinToString(",")}"
}

/** Device-wide VRAM usage includes other applications; it is not process-private memory. */
internal fun gpuMb(): String = runCatching {
    val process = ProcessBuilder("C:/Windows/System32/nvidia-smi.exe", "--query-gpu=memory.used", "--format=csv,noheader,nounits")
        .redirectErrorStream(true).start()
    check(process.waitFor(5, TimeUnit.SECONDS)) { "nvidia-smi timeout" }
    process.inputStream.bufferedReader().readText().trim()
}.getOrElse { "unavailable" }

internal fun hardwareMs(since: Long): Long = (System.nanoTime() - since) / 1_000_000
