package com.woka.feature.viewer.video.engine.decode

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.javacv.Frame
import java.lang.foreign.MemorySegment

/** DV5-102: metadata snapshot + native rows, complete before FFmpeg can advance or seek. */
internal class DoviColourConverter(preferGpu: Boolean = true) : AutoCloseable {
    private var context = DoviColourNative.create()
    private var output: DoviPicture? = null
    private var gpuEnabled = preferGpu

    fun convert(frame: Frame, width: Int = frame.imageWidth, height: Int = frame.imageHeight): Frame {
        check(context != MemorySegment.NULL)
        val source = frame.opaque as AVFrame
        DoviColourNative.prepare(context, MemorySegment.ofAddress(source.address()))
        try {
            if (gpuEnabled) {
                val target = target(width, height)
                if (DoviGpuNative.render(context, target)) {
                    target.copyProperties(source)
                    return target.frame(frame)
                }
                // No retry/allocation churn on unavailable devices or unsupported layouts.
                gpuEnabled = false
            }
            val target = target(source.width(), source.height())
            val stride = target.picture.linesize(0)
            val height = target.picture.height()
            val pixels = MemorySegment.ofAddress(target.picture.data(0).address())
            DoviRowWorkers.render(height) { first, end ->
                DoviColourNative.rows(context, pixels, stride.toLong() * height, stride, first, end)
            }
            target.copyProperties(source)
            return target.frame(frame)
        } finally {
            DoviColourNative.finish(context)
        }
    }

    private fun target(width: Int, height: Int): DoviPicture {
        var target = output
        if (target == null || target.picture.width() != width || target.picture.height() != height) {
            target?.close()
            output = null
            target = DoviPicture(width, height)
            output = target
        }
        target.writable()
        return target
    }

    override fun close() {
        if (context == MemorySegment.NULL) return
        try {
            output?.close()
            output = null
        } finally {
            DoviColourNative.destroy(context)
            context = MemorySegment.NULL
        }
    }
}
