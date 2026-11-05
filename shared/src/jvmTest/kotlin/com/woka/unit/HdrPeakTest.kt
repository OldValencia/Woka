package com.woka.unit

import com.woka.core.data.TransferKind
import com.woka.feature.viewer.video.ASSUMED_WHITE_POINT
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import com.woka.feature.viewer.video.engine.probe.declaredWhitePoint
import com.woka.feature.viewer.video.engine.probe.plausibleWhitePoint
import com.woka.feature.viewer.video.engine.probe.readFrom
import com.woka.feature.viewer.video.engine.probe.scenePeak
import com.woka.feature.viewer.video.engine.probe.toColour
import com.woka.feature.viewer.video.engine.probe.withSceneOf
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avutil.av_content_light_metadata_create_side_data
import org.bytedeco.ffmpeg.global.avutil.av_dynamic_hdr_plus_create_side_data
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_mastering_display_metadata_create_side_data
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

private const val SLACK = 0.001f

/** ME-301 / ME-305: the white point a film declares, checked, and the scene's HDR10+ peak. */
class HdrPeakTest {

    @Test
    fun `MaxCLL is the answer when it is believable`() {
        // Green Book: MaxCLL 717, MaxFALL 168, mastered at 1000.
        assertEquals(7.17f, plausibleWhitePoint(717, 168, 1000.0), SLACK)
    }

    @Test
    fun `a missing or broken MaxCLL falls to the mastering display`() {
        assertEquals(10f, plausibleWhitePoint(0, 0, 1000.0), SLACK)
        assertEquals(10f, plausibleWhitePoint(227, 300, 1000.0), SLACK, "a peak under the average was believed")
        assertEquals(10f, plausibleWhitePoint(4000, 0, 1000.0), SLACK, "MaxCLL above the mastering display was not capped")
    }

    @Test
    fun `nothing believable keeps the assumed white point`() {
        assertEquals(ASSUMED_WHITE_POINT, plausibleWhitePoint(0, 0, null))
        assertEquals(ASSUMED_WHITE_POINT, plausibleWhitePoint(0, 0, 50.0), "a 50-nit mastering display was believed")
        assertEquals(ASSUMED_WHITE_POINT, plausibleWhitePoint(20_000, 0, null))
    }

    @Test
    fun `never below SDR white`() {
        assertEquals(1f, plausibleWhitePoint(60, 0, null))
    }

    @Test
    fun `read off a frame's own side data`() = withFrame { frame ->
        assertEquals(ASSUMED_WHITE_POINT, frame.declaredWhitePoint(), "a bare frame declared something")
        av_content_light_metadata_create_side_data(frame).MaxCLL(717).MaxFALL(168)
        av_mastering_display_metadata_create_side_data(frame).apply {
            max_luminance(AVRational().num(1000).den(1))
            has_luminance(1)
        }
        assertEquals(7.17f, frame.declaredWhitePoint(), SLACK)
    }

    @Test
    fun `read once, and never for SDR`() = withFrame { frame ->
        av_content_light_metadata_create_side_data(frame).MaxCLL(717).MaxFALL(168)
        val sdr = HdrInfo(TransferKind.SDR, isWideGamut = false)
        assertSame(sdr, sdr.readFrom(frame))
        val hlg = HdrInfo(TransferKind.HLG, isWideGamut = true)
        assertSame(hlg, hlg.readFrom(frame), "an HLG film took a PQ white point")
        val read = HdrInfo(TransferKind.PQ, isWideGamut = true, whitePoint = 3f)
        assertSame(read, read.readFrom(frame), "a film's white point was read twice")
        assertEquals(7.17f, HdrInfo(TransferKind.PQ, isWideGamut = true).readFrom(frame).whitePoint!!, SLACK)
    }

    @Test
    fun `the HDR10+ scene peak, and the colour prefers it`() = withFrame { frame ->
        assertNull(frame.scenePeak(), "a frame without HDR10+ had a scene")
        av_dynamic_hdr_plus_create_side_data(frame).apply {
            num_windows(1.toByte())
            // The Boys S05E01 at 4:58, as ffprobe printed it: the blue channel is the peak.
            params(0).maxscl(0, AVRational().num(3920).den(100_000))
            params(0).maxscl(1, AVRational().num(3106).den(100_000))
            params(0).maxscl(2, AVRational().num(6891).den(100_000))
        }
        assertEquals(6.891f, frame.scenePeak()!!, SLACK)

        val film = HdrInfo(TransferKind.PQ, isWideGamut = true, whitePoint = 9.13f)
        assertEquals(6.891f, film.withSceneOf(frame).toColour().whitePoint!!, SLACK)
        assertEquals(9.13f, film.toColour().whitePoint!!, SLACK)
        val hlg = HdrInfo(TransferKind.HLG, isWideGamut = true)
        assertSame(hlg, hlg.withSceneOf(frame), "an HLG film took an HDR10+ scene")
    }

    private fun withFrame(block: (AVFrame) -> Unit) {
        val frame = av_frame_alloc()
        try {
            block(frame)
        } finally {
            av_frame_free(frame)
        }
    }
}
