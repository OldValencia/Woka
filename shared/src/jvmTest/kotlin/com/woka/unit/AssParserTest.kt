package com.woka.unit

import com.woka.feature.viewer.video.engine.subtitle.parseAss
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `Dialogue: Layer,Start,End,Style,Name,ML,MR,MV,Effect,Text` — nine fields, then the text. */
private fun dialogue(start: String, end: String, text: String, style: String = "Default") =
    "Dialogue: 0,$start,$end,$style,,0,0,0,,$text"

class AssParserTest {

    @Test
    fun `a dialogue line becomes a cue`() {
        val cues = parseAss(dialogue("0:00:01.00", "0:00:06.00", "Hello"))
        assertEquals(1, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(6000L, cues[0].endMs)
        assertEquals("Hello", cues[0].text)
    }

    @Test
    fun `the fraction is centiseconds`() {
        val cues = parseAss(dialogue("0:00:01.50", "1:02:03.07", "Timed"))
        assertEquals(1500L, cues[0].startMs)
        assertEquals(3_723_070L, cues[0].endMs)
    }

    @Test
    fun `override tags are stripped and their text is kept`() {
        val cues = parseAss(dialogue("0:00:01.00", "0:00:02.00", "{\\i1}Italic{\\i0} and {\\b1}bold{\\b0}"))
        assertEquals("Italic and bold", cues[0].text)
    }

    @Test
    fun `a positioning tag leaves nothing behind`() {
        val cues = parseAss(dialogue("0:00:01.00", "0:00:02.00", "{\\an8\\pos(320,50)}Overhead"))
        assertEquals("Overhead", cues[0].text)
    }

    @Test
    fun `an escaped line break becomes a real one`() {
        val cues = parseAss(dialogue("0:00:01.00", "0:00:02.00", "First\\NSecond"))
        assertEquals("First\nSecond", cues[0].text)
    }

    @Test
    fun `a hard space is not left as an escape`() {
        val cues = parseAss(dialogue("0:00:01.00", "0:00:02.00", "Wide\\hgap"))
        assertEquals("Wide gap", cues[0].text)
    }

    @Test
    fun `commas inside the text are not treated as field separators`() {
        val line = dialogue("0:00:01.00", "0:00:02.00", "Yes, of course, sir.")
        assertEquals("Yes, of course, sir.", parseAss(line)[0].text)
    }

    @Test
    fun `styles beyond Default are parsed the same`() {
        val text = listOf(
            dialogue("0:00:01.00", "0:00:02.00", "Speech", style = "Default"),
            dialogue("0:00:02.00", "0:00:03.00", "Sign", style = "Sign"),
            dialogue("0:00:03.00", "0:00:04.00", "Song", style = "Karaoke")
        ).joinToString("\n")
        assertEquals(listOf("Speech", "Sign", "Song"), parseAss(text).map { it.text })
    }

    @Test
    fun `script headers and style definitions are ignored`() {
        val text = """
            [Script Info]
            ScriptType: v4.00+
            PlayResX: 1920

            [V4+ Styles]
            Format: Name, Fontname, Fontsize
            Style: Default,Arial,48

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            ${dialogue("0:00:01.00", "0:00:02.00", "Only this")}
        """.trimIndent()
        assertEquals(listOf("Only this"), parseAss(text).map { it.text })
    }

    @Test
    fun `a commented-out line is not rendered`() {
        val commented = dialogue("0:00:01.00", "0:00:02.00", "Hidden").replaceFirst("Dialogue", "Comment")
        assertTrue(parseAss(commented).isEmpty())
    }

    @Test
    fun `a malformed timestamp drops the line instead of the file`() {
        val text = listOf(
            dialogue("not-a-time", "0:00:02.00", "Broken"),
            dialogue("0:00:03.00", "0:00:04.00", "Fine")
        ).joinToString("\n")
        assertEquals(listOf("Fine"), parseAss(text).map { it.text })
    }

    @Test
    fun `a truncated dialogue line is skipped`() {
        assertTrue(parseAss("Dialogue: 0,0:00:01.00,0:00:02.00,Default").isEmpty())
    }

    @Test
    fun `a cue whose text is only tags is dropped`() {
        assertTrue(parseAss(dialogue("0:00:01.00", "0:00:02.00", "{\\fad(200,200)}")).isEmpty())
    }

    @Test
    fun `an empty file yields no cues`() {
        assertEquals(emptyList(), parseAss(""))
    }
}
