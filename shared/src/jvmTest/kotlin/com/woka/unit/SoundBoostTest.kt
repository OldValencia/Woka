package com.woka.unit

import com.woka.feature.viewer.video.engine.audio.boostFactor
import com.woka.feature.viewer.video.engine.audio.boosted
import com.woka.feature.viewer.video.engine.audio.lineLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Volume past 100 %, in the one place it can be checked without a sound card.
 *
 * **The assertion that matters is the clamp.** A sample multiplied past `Short.MAX_VALUE`
 * and allowed to overflow flips sign, and a waveform that jumps to its own negative is a
 * crack rather than a loud note — the failure would sound like a broken file, not like a
 * boost that went too far.
 */
class SoundBoostTest {

    /** One 16-bit signed little-endian sample, the format `initAudio` opens the line as. */
    private fun pcm(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, value ->
            out[index * 2] = (value and 0xFF).toByte()
            out[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun samplesOf(pcm: ByteArray): List<Int> =
        (pcm.indices step 2).map { i ->
            ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
        }

    @Test
    fun `the level splits at unity, and the two halves meet there`() {
        assertEquals(0.4f, lineLevel(0.4f))
        assertEquals(1f, boostFactor(0.4f), "below unity nothing is scaled by hand")

        assertEquals(1f, lineLevel(2.5f), "the line was asked for more than it can be trusted with")
        assertEquals(2.5f, boostFactor(2.5f))

        assertEquals(lineLevel(1f) * boostFactor(1f), 1f, "the two disagree at 100 %")
    }

    @Test
    fun `a factor of one hands the buffer straight back`() {
        val buffer = pcm(1000, -1000)
        assertSame(buffer, boosted(buffer, 1f), "the ordinary path allocated a copy")
        assertSame(buffer, boosted(buffer, 0.5f), "below unity is the line's work, not ours")
    }

    @Test
    fun `samples are scaled, and the source is left alone`() {
        val buffer = pcm(1000, -1000, 0)
        val out = boosted(buffer, 2f)

        assertEquals(listOf(2000, -2000, 0), samplesOf(out))
        assertEquals(listOf(1000, -1000, 0), samplesOf(buffer), "the channel's buffer was mutated")
    }

    @Test
    fun `a sample driven past full scale clips instead of wrapping`() {
        val out = samplesOf(boosted(pcm(20_000, -20_000), 3f))

        assertEquals(Short.MAX_VALUE.toInt(), out[0])
        assertEquals(Short.MIN_VALUE.toInt(), out[1])
        assertTrue(out[0] > 0 && out[1] < 0, "an overflowed sample flipped sign")
    }

    /** Cannot happen with a frame-aligned buffer, and is copied through rather than trusted. */
    @Test
    fun `an odd trailing byte survives`() {
        val out = boosted(pcm(1000) + 7.toByte(), 2f)

        assertEquals(3, out.size)
        assertEquals(7.toByte(), out[2])
    }
}
