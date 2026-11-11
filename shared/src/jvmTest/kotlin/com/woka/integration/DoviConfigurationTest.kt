package com.woka.integration

import com.woka.feature.viewer.video.engine.probe.DoviConfiguration
import com.woka.feature.viewer.video.engine.probe.DoviConfigurationRead
import com.woka.feature.viewer.video.engine.probe.DoviNative
import com.woka.feature.viewer.video.engine.probe.readDoviConfiguration
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DoviConfigurationTest {
    @Test
    fun `the typed reader recognises P5 and compatible Dolby profiles`() {
        val samples = listOf(
            "Mystery Box Dolby Vision Profile 5.mp4" to DoviConfiguration(5, 0),
            "Test Jellyfin 1080p DV P5.mp4" to DoviConfiguration(5, 0),
            "Test Jellyfin 4K DV P8.1.mp4" to DoviConfiguration(8, 1),
            "Test Jellyfin 4K DV P8.4.mp4" to DoviConfiguration(8, 4),
            "Hybrid HDR10Plus DV Sample.mkv" to DoviConfiguration(7, 6),
            "DVprofile10.mkv" to DoviConfiguration(10, 1)
        )
        assumeTrue("The author's DV samples are required", samples.all { source(it.first).isFile })
        for ((name, expected) in samples) {
            val actual = assertIs<DoviConfigurationRead.Present>(read(source(name)), name)
            assertEquals(expected, actual.configuration, name)
            println("DOVI_TYPED $name profile=${actual.configuration.profile} compatibility=${actual.configuration.compatibility}")
        }
    }

    @Test
    fun `ordinary SDR and HDR10 streams have no Dolby configuration`() {
        for (name in listOf("Test Jellyfin 8K HEVC 10bit 150M.mp4", "Test Jellyfin 8K HEVC HDR10 150M.mp4")) {
            assumeTrue("The author's 8K controls are required", source(name).isFile)
            assertEquals(DoviConfigurationRead.Absent, read(source(name)), name)
        }
    }

    @Test
    fun `null and short native records are rejected before reading fields`() {
        val absent = assertIs<DoviConfigurationRead.Unavailable>(DoviNative.read(null, 0L))
        assertTrue(absent.reason.contains("incomplete"), absent.reason)
        BytePointer(8L).use { bytes ->
            for (size in 0L..8L) {
                val result = assertIs<DoviConfigurationRead.Unavailable>(DoviNative.read(bytes, size))
                assertTrue(result.reason.contains("incomplete"), result.reason)
            }
        }
    }

    @Test
    fun `a different runtime library version is rejected before dereferencing the pointer`() {
        val result = assertIs<DoviConfigurationRead.Unavailable>(DoviNative.read(null, 0L, version = 0))
        assertTrue(result.reason.contains("does not match"), result.reason)
    }

    @Test
    fun `repeated header reads close the source before the next open`() {
        val file = source("Test Jellyfin 1080p DV P5.mp4")
        assumeTrue("The author's P5 sample is required", file.isFile)
        repeat(12) {
            assertEquals(DoviConfigurationRead.Present(DoviConfiguration(5, 0)), read(file))
        }
    }

    private fun source(name: String) = File("D:/Movies/DV samples", name)

    private fun read(file: File): DoviConfigurationRead {
        val grabber = FFmpegFrameGrabber(file).apply {
            imageMode = FrameGrabber.ImageMode.RAW
            audioStream = Int.MAX_VALUE
            setVideoOption("threads", "2")
        }
        try {
            grabber.start()
            val parameters = grabber.formatContext.streams(grabber.videoStream).codecpar()
            return readDoviConfiguration(parameters)
        } finally {
            runCatching { grabber.stop() }
            grabber.release()
        }
    }
}
