package com.woka.probe

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVCodecContext.Get_format_AVCodecContext_IntPointer
import org.bytedeco.ffmpeg.avutil.AVBufferRef
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_CUDA
import org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_D3D11VA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_CUDA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_D3D11
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_copy_props
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_move_ref
import org.bytedeco.ffmpeg.global.avutil.av_frame_remove_side_data
import org.bytedeco.ffmpeg.global.avutil.av_frame_unref
import org.bytedeco.ffmpeg.global.avutil.av_hwdevice_ctx_create
import org.bytedeco.ffmpeg.global.avutil.av_hwframe_transfer_data
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.io.File

/**
 * TEMPORARY (ME-236): native HEVC parsing with an explicit GPU context, not `hevc_cuvid`.
 * JavaCV lacks the device setup and download before its private `processImage`, so this
 * probe supplies both through reflection. It leaves the decoder's colour conversion and
 * side data intact. This is evidence about the missing adapter, not production code.
 */
internal class HardwareContextGrabber(file: File, private val mode: String) : FFmpegFrameGrabber(file) {
    private val grabberType = FFmpegFrameGrabber::class.java
    private val gpuFormat = if (mode == "cuda-native") AV_PIX_FMT_CUDA else AV_PIX_FMT_D3D11
    private val deviceType = if (mode == "cuda-native") AV_HWDEVICE_TYPE_CUDA else AV_HWDEVICE_TYPE_D3D11VA
    private val downloaded = av_frame_alloc()
    private var disposed = false
    var transfers = 0
        private set
    private val choose = object : Get_format_AVCodecContext_IntPointer() {
        override fun call(context: AVCodecContext, formats: IntPointer): Int {
            var index = 0L
            val offered = mutableListOf<Int>()
            while (formats.get(index) != AV_PIX_FMT_NONE) {
                offered += formats.get(index)
                index++
            }
            println("ME236 get_format offers=$offered requested=$gpuFormat")
            if (gpuFormat in offered) return gpuFormat
            return AV_PIX_FMT_NONE
        }
    }

    override fun start() {
        super.start()
        val device = AVBufferRef(null as Pointer?)
        val id = if (mode == "cuda-native") "0" else "1"
        val created = av_hwdevice_ctx_create(device, deviceType, id, null, 0)
        check(created >= 0) { "Hardware device $mode creation failed: $created" }
        val old = codec()
        val decoder = avcodec_find_decoder(old.codec_id())
        val context = avcodec_alloc_context3(decoder)
        context.hw_device_ctx(device)
        try {
            check(avcodec_parameters_to_context(context, formatContext.streams(videoStream).codecpar()) >= 0)
            context.thread_count(4)
            context.pkt_timebase(formatContext.streams(videoStream).time_base())
            context.get_format(choose)
            check(avcodec_open2(context, decoder, null as AVDictionary?) >= 0) { "GPU codec open failed" }
            val field = grabberType.getDeclaredField("video_c").apply { isAccessible = true }
            field.set(this, context)
            avcodec_free_context(old)
        } catch (failure: Throwable) {
            avcodec_free_context(context)
            throw failure
        }
    }

    @Synchronized
    override fun grabFrame(audio: Boolean, video: Boolean, processing: Boolean, keys: Boolean, data: Boolean): Frame? {
        val rgb = field("picture_rgb") as? AVFrame
        if (rgb != null && !rgb.isNull) {
            while (rgb.nb_side_data() > 0) av_frame_remove_side_data(rgb, rgb.side_data(0).type())
        }
        val frame = super.grabFrame(audio, video, false, keys, data) ?: return null
        if (!processing || frame.image == null) return frame
        val picture = field("picture") as AVFrame
        check(picture.format() == gpuFormat) { "Decoder silently returned software format ${picture.format()}" }
        av_frame_unref(downloaded)
        val transferred = av_hwframe_transfer_data(downloaded, picture, 0)
        check(transferred >= 0) { "GPU frame download failed: $transferred" }
        check(av_frame_copy_props(downloaded, picture) >= 0) { "Frame properties copy failed" }
        av_frame_unref(picture)
        av_frame_move_ref(picture, downloaded)
        transfers++
        val context = codec()
        val originalFormat = context.pix_fmt()
        try {
            context.pix_fmt(picture.format())
            process.invoke(this)
        } finally {
            context.pix_fmt(originalFormat)
        }
        return frame
    }

    override fun release() {
        try {
            super.release()
        } finally {
            if (!disposed) {
                av_frame_free(downloaded)
                choose.close()
                disposed = true
            }
        }
    }

    private fun codec(): AVCodecContext = field("video_c") as AVCodecContext

    private fun field(name: String): Any? {
        val field = grabberType.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
    }

    private val process = grabberType.getDeclaredMethod("processImage").apply { isAccessible = true }
}
