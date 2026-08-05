package com.woka.unit

import com.woka.feature.viewer.shell.audioDetail
import com.woka.feature.viewer.shell.formatBitrate
import com.woka.feature.viewer.shell.formatDuration
import com.woka.feature.viewer.shell.formatFps
import com.woka.feature.viewer.shell.resolution
import com.woka.feature.viewer.shell.subtitleDetail
import com.woka.feature.viewer.shell.subtitleFormat
import com.woka.feature.viewer.shell.toReadableSize
import com.woka.feature.viewer.video.model.MediaTrack
import com.woka.feature.viewer.video.model.TrackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fractional formatters go through `String.format`, which uses the default
 * locale — the decimal mark is `,` in half of Europe. Those assertions match
 * either mark rather than pinning the suite to one machine's locale.
 */
private fun assertDecimal(pattern: String, actual: String) {
    val regex = Regex(pattern.replace("#", "[.,]"))
    assertTrue(actual.matches(regex), "expected '$pattern' (# = decimal mark) but got '$actual'")
}

private fun track(type: TrackType, codec: String, block: MediaTrack.() -> MediaTrack = { this }) =
    MediaTrack(index = 0, number = 1, type = type, codec = codec).block()

class FileInfoFormatTest {

    @Test
    fun `a duration under a minute still shows its minutes field`() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:07", formatDuration(7_400L))
        assertEquals("0:59", formatDuration(59_999L))
    }

    @Test
    fun `the hours field appears only once there are hours`() {
        assertEquals("1:00", formatDuration(60_000L))
        assertEquals("59:59", formatDuration(3_599_000L))
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("12:34:56", formatDuration(45_296_000L))
    }

    @Test
    fun `bytes below a kilobyte are counted exactly`() {
        assertEquals("0 B", 0L.toReadableSize())
        assertEquals("1023 B", 1023L.toReadableSize())
    }

    @Test
    fun `kilobytes are whole and turn over at 1024`() {
        assertEquals("1 KB", 1024L.toReadableSize())
        assertEquals("1023 KB", (1024L * 1024 - 1).toReadableSize())
    }

    @Test
    fun `megabytes and gigabytes carry two decimals`() {
        assertDecimal("1#00 MB", (1024L * 1024).toReadableSize())
        assertDecimal("3#30 MB", (3_460_300L).toReadableSize())
        assertDecimal("1#00 GB", (1024L * 1024 * 1024).toReadableSize())
        assertDecimal("2#50 GB", (1024L * 1024 * 1024 * 5 / 2).toReadableSize())
    }

    @Test
    fun `bitrate switches unit at each thousand`() {
        assertEquals("0 bps", formatBitrate(0L))
        assertEquals("999 bps", formatBitrate(999L))
        assertEquals("1 kbps", formatBitrate(1_000L))
        assertEquals("999 kbps", formatBitrate(999_999L))
        assertDecimal("1#0 Mbps", formatBitrate(1_000_000L))
        assertDecimal("8#5 Mbps", formatBitrate(8_500_000L))
    }

    @Test
    fun `a whole frame rate loses its decimals`() {
        assertEquals("30 fps", formatFps(30.0))
        assertEquals("60 fps", formatFps(60.0))
        assertEquals("25 fps", formatFps(25.0))
    }

    @Test
    fun `a fractional frame rate keeps only the digits it needs`() {
        assertDecimal("23#5 fps", formatFps(23.5))
        assertDecimal("12#125 fps", formatFps(12.125))
        // The broadcast rates, where a truncating conversion would show 29.969.
        assertDecimal("29#97 fps", formatFps(29.97))
        assertDecimal("23#976 fps", formatFps(23.976))
        assertDecimal("59#94 fps", formatFps(59.94))
    }

    @Test
    fun `subtitle codecs get their familiar names`() {
        assertEquals("SRT", subtitleFormat("subrip"))
        assertEquals("ASS", subtitleFormat("ass"))
        assertEquals("ASS", subtitleFormat("SSA"))
        assertEquals("PGS", subtitleFormat("hdmv_pgs_subtitle"))
        assertEquals("VobSub", subtitleFormat("dvd_subtitle"))
        assertEquals("WebVTT", subtitleFormat("webvtt"))
    }

    @Test
    fun `an unmapped codec is shown as it comes`() {
        assertEquals("MICRODVD", subtitleFormat("microdvd"))
    }

    @Test
    fun `resolution needs both dimensions`() {
        assertEquals("1920×1080", track(TrackType.Video, "h264") { copy(width = 1920, height = 1080) }.resolution())
        assertNull(track(TrackType.Video, "h264") { copy(width = 1920) }.resolution())
        assertNull(track(TrackType.Video, "h264").resolution())
    }

    @Test
    fun `track details drop the parts a stream does not carry`() {
        val full = track(TrackType.Audio, "ac3") { copy(channelLayout = "5.1") }
        assertEquals("AC3 · 5.1", audioDetail(full))
        assertEquals("AAC", audioDetail(track(TrackType.Audio, "aac")))
        val subtitle = track(TrackType.Subtitle, "subrip") { copy(language = "rus") }
        assertEquals("SRT · rus", subtitleDetail(subtitle))
        assertEquals("SRT", subtitleDetail(track(TrackType.Subtitle, "subrip")))
    }
}
