package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.ThreadedVideoConverter
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_copy_props
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_opt_set_int
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_alloc_context
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale_frame
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test

/** DV5-104: isolate RGBA resize from colour reconstruction and bitmap copies. */
class DoviScaleProbe {
    @Test
    fun `compare legacy and dynamic scaling of reconstructed P5`() {
        assumeTrue(System.getProperty("woka.probe.dv5") == "true")
        val report = mutableListOf<String>()
        FreshFrameGrabber(File("D:/Movies/DV samples/Mystery Box Dolby Vision Profile 5.mp4")).use { grabber ->
            grabber.pixelFormat = AV_PIX_FMT_RGBA
            grabber.setVideoOption("threads", "4")
            grabber.start()
            for (time in listOf(1_001_000L, 5_005_000L)) {
                val frame = checkNotNull(grabber.grabImageAt(time))
                for (width in listOf(1920, 1280, 480)) {
                    val result = measure(frame.opaque as AVFrame, width)
                    report += "pts=$time $result"
                    println("DV5-SCALE ${report.last()}")
                }
            }
        }
        File("build/dv-p5").mkdirs()
        File("build/dv-p5/scale-throughput.txt").writeText(report.joinToString("\n"))
    }

    private fun measure(source: AVFrame, width: Int): String {
        val height = source.height() * width / source.width()
        val output = checkNotNull(av_frame_alloc())
        val context = sws_alloc_context()
        try {
            output.width(width).height(height).format(AV_PIX_FMT_RGBA)
            check(av_frame_get_buffer(output, 32) >= 0)
            check(av_frame_copy_props(output, source) >= 0)
            check(av_opt_set_int(context, "sws_flags", SWS_BILINEAR.toLong(), 0) >= 0)
            check(av_opt_set_int(context, "threads", 4, 0) >= 0)
            ThreadedVideoConverter(width, height).use { legacy ->
                var legacyNs = 0L
                var dynamicNs = 0L
                repeat(130) { index ->
                    val begin = System.nanoTime()
                    legacy.convert(source, 0)
                    val middle = System.nanoTime()
                    check(sws_scale_frame(context, output, source) >= 0)
                    val end = System.nanoTime()
                    if (index >= 10) {
                        legacyNs += middle - begin
                        dynamicNs += end - middle
                    }
                }
                val old = legacy.convert(source, 0).opaque as AVFrame
                val a = old.data(0).capacity(old.linesize(0).toLong() * height).asByteBuffer()
                val b = output.data(0).capacity(output.linesize(0).toLong() * height).asByteBuffer()
                var max = 0
                var total = 0L
                var changed = 0L
                for (y in 0 until height) for (x in 0 until width * 4) {
                    val difference = abs((a.get(y * old.linesize(0) + x).toInt() and 255) -
                        (b.get(y * output.linesize(0) + x).toInt() and 255))
                    max = maxOf(max, difference)
                    total += difference
                    if (difference > 1) changed++
                }
                return String.format(Locale.ROOT,
                    "%dx%d legacyMs=%.3f dynamicMs=%.3f maxByte=%d meanByte=%.6f over1=%d tags=%d/%d/%d/%d",
                    width, height, legacyNs / 120e6, dynamicNs / 120e6, max, total.toDouble() / (width * height * 4),
                    changed, output.colorspace(), output.color_primaries(), output.color_trc(), output.color_range())
            }
        } finally {
            sws_freeContext(context)
            av_frame_free(output)
        }
    }
}
