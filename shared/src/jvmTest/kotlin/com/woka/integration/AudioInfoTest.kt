package com.woka.integration

import com.woka.feature.viewer.shell.formatSampleRate
import com.woka.feature.viewer.video.engine.probe.probeMediaTracks
import com.woka.support.onGrabber
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What Info shows for a track (AP-003): the stream's rate and bitrate, read by the
 * same probe a film's tracks go through. `tone-cover.mp3` is 22.05 kHz mono at 48 kbps.
 */
class AudioInfoTest {

    private fun tone(name: String) = File(javaClass.getResource("/media/audio/$name")!!.toURI())

    @Test
    fun `an audio stream carries its sample rate and bitrate`() {
        val audio = onGrabber(tone("tone-cover.mp3")) { probeMediaTracks(it) }.audio.single()
        assertEquals(22_050, audio.sampleRate)
        assertEquals(48_000L, audio.bitRate)
    }

    @Test
    fun `a sample rate reads in kilohertz without a needless decimal`() {
        assertEquals("48 kHz", formatSampleRate(48_000))
        assertEquals("44.1 kHz", formatSampleRate(44_100).replace(',', '.'))
    }
}
