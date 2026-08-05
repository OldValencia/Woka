package com.woka.unit

import com.woka.feature.viewer.video.engine.subtitle.parseSrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SrtParserTest {

    @Test
    fun `a cue keeps its own line breaks`() {
        val cues = parseSrt(
            """
            1
            00:00:01,000 --> 00:00:06,000
            Line one
            Line two
            """.trimIndent()
        )
        assertEquals(1, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(6000L, cues[0].endMs)
        assertEquals("Line one\nLine two", cues[0].text)
    }

    @Test
    fun `overlapping cues are both kept, in file order`() {
        val cues = parseSrt(
            """
            1
            00:00:01,000 --> 00:00:09,000
            Early

            2
            00:00:03,000 --> 00:00:05,000
            Interjection
            """.trimIndent()
        )
        assertEquals(listOf("Early", "Interjection"), cues.map { it.text })
        assertTrue(cues[1].startMs < cues[0].endMs, "the fixture must actually overlap")
    }

    @Test
    fun `a block with no text is dropped`() {
        val cues = parseSrt("1\n00:00:01,000 --> 00:00:02,000\n\n\n2\n00:00:03,000 --> 00:00:04,000\nHere")
        assertEquals(listOf("Here"), cues.map { it.text })
    }

    @Test
    fun `a block with no timing line is skipped rather than shifting the rest`() {
        val cues = parseSrt("junk\nnot a cue\n\n1\n00:00:02,000 --> 00:00:03,000\nReal")
        assertEquals(listOf("Real"), cues.map { it.text })
        assertEquals(2000L, cues[0].startMs)
    }

    @Test
    fun `windows line endings parse the same as unix`() {
        val unix = parseSrt("1\n00:00:01,000 --> 00:00:02,000\nA\n\n2\n00:00:03,000 --> 00:00:04,000\nB")
        val dos = parseSrt(
            "1\r\n00:00:01,000 --> 00:00:02,000\r\nA\r\n\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nB"
        )
        assertEquals(unix, dos)
        assertEquals(2, dos.size)
    }

    @Test
    fun `a zero-duration cue survives instead of being discarded`() {
        val cues = parseSrt("1\n00:00:02,000 --> 00:00:02,000\nFlash")
        assertEquals(1, cues.size)
        assertEquals(cues[0].startMs, cues[0].endMs)
    }

    @Test
    fun `hours minutes and seconds all carry`() {
        val cues = parseSrt("1\n01:02:03,004 --> 01:02:04,000\nLate")
        assertEquals(3_723_004L, cues[0].startMs)
    }

    @Test
    fun `a short fraction is tenths, not milliseconds`() {
        val cues = parseSrt("1\n00:00:01,5 --> 00:00:02,25\nFast")
        assertEquals(1500L, cues[0].startMs)
        assertEquals(2250L, cues[0].endMs)
    }

    @Test
    fun `a dot is accepted where the standard wants a comma`() {
        val cues = parseSrt("1\n00:00:01.250 --> 00:00:02.500\nDotted")
        assertEquals(1250L, cues[0].startMs)
        assertEquals(2500L, cues[0].endMs)
    }

    @Test
    fun `inline markup is stripped from the text`() {
        val cues = parseSrt("1\n00:00:01,000 --> 00:00:02,000\n<i>Whispered</i> aloud")
        assertEquals("Whispered aloud", cues[0].text)
    }

    @Test
    fun `angle-bracketed dialogue is not mistaken for markup`() {
        val cues = parseSrt("1\n00:00:01,000 --> 00:00:02,000\n<<SIGN>> Exit")
        assertEquals("<<SIGN>> Exit", cues[0].text)
    }

    @Test
    fun `an empty file yields no cues rather than throwing`() {
        assertEquals(emptyList(), parseSrt(""))
        assertEquals(emptyList(), parseSrt("\n\n\n"))
    }
}
