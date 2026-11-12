package com.woka.feature.viewer.video.engine.decode

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVCodecContext.Get_format_AVCodecContext_IntPointer
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVBufferRef
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_CUDA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_CUDA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE
import org.bytedeco.ffmpeg.global.avutil.av_buffer_unref
import org.bytedeco.ffmpeg.global.avutil.av_hwdevice_ctx_create
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.Pointer

/**
 * Owns a native HEVC decoder and its CUDA device, never JavaCV's private codec (EK-002).
 * `hevc_cuvid` loses HDR/DV side data; native HEVC with a device preserves it in the
 * 2026-11-12 parity probe. The callback stays alive until after codec teardown.
 */
internal class CudaVideoCodec private constructor() : AutoCloseable {
    private var ownedContext: AVCodecContext? = null
    val context: AVCodecContext get() = checkNotNull(ownedContext)
    private val choose = object : Get_format_AVCodecContext_IntPointer() {
        override fun call(context: AVCodecContext, formats: IntPointer): Int {
            var index = 0L
            while (formats.get(index) != AV_PIX_FMT_NONE) {
                if (formats.get(index) == AV_PIX_FMT_CUDA) return AV_PIX_FMT_CUDA
                index++
            }
            return AV_PIX_FMT_NONE
        }
    }
    private var closed = false

    private fun initialise(stream: AVStream) {
        val codec = avcodec_find_decoder(stream.codecpar().codec_id())
        check(codec != null && !codec.isNull) { "Native HEVC decoder unavailable" }
        ownedContext = avcodec_alloc_context3(codec)
        check(ownedContext != null && !context.isNull) { "Cannot allocate CUDA codec" }
        check(avcodec_parameters_to_context(context, stream.codecpar()) >= 0)
        val device = AVBufferRef(null as Pointer?)
        try {
            val result = av_hwdevice_ctx_create(device, AV_HWDEVICE_TYPE_CUDA, null as String?, null, 0)
            check(result >= 0) { "CUDA device unavailable ($result)" }
            context.hw_device_ctx(device)
        } catch (failure: Throwable) {
            if (!device.isNull) av_buffer_unref(device)
            throw failure
        }
        context.thread_count(4)
        context.pkt_timebase(stream.time_base())
        context.get_format(choose)
        val result = avcodec_open2(context, codec, null as AVDictionary?)
        check(result >= 0) { "CUDA codec open failed ($result)" }
    }

    override fun close() {
        if (closed) return
        closed = true
        ownedContext?.let { if (!it.isNull) avcodec_free_context(it) }
        ownedContext = null
        choose.close()
    }

    companion object {
        fun open(stream: AVStream): CudaVideoCodec {
            val owner = CudaVideoCodec()
            try {
                owner.initialise(stream)
                return owner
            } catch (failure: Throwable) {
                owner.close()
                throw failure
            }
        }
    }
}
