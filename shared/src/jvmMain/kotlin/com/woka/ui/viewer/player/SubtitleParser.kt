package com.woka.ui.viewer.player

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avcodec.AVSubtitle
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.global.avcodec.SUBTITLE_ASS
import org.bytedeco.ffmpeg.global.avcodec.SUBTITLE_TEXT
import org.bytedeco.ffmpeg.global.avcodec.avcodec_decode_subtitle2
import org.bytedeco.ffmpeg.global.avcodec.avsubtitle_free
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.javacpp.IntPointer

/**
 * Helper utilities for cleaning subtitle text extracted from SRT, ASS, SSA,
 * or text subtitle streams (§2.2 / §3).
 */
internal object SubtitleParser {

    private val assPrefixRegex = Regex("^.*?,\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*,[^,]*,")

    /**
     * Cleans raw subtitle text from AVSubtitleRect by stripping ASS dialogue
     * prefixes, style override tags ({...}), and normalizing line breaks.
     */
    fun cleanText(raw: String, isAss: Boolean = false): String {
        if (raw.isBlank()) return ""
        var text = raw.trim()
        if (isAss || text.startsWith("Dialogue:", ignoreCase = true) || text.startsWith("Comment:", ignoreCase = true)) {
            text = extractAssText(text)
        }
        text = text.replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
        text = stripTags(text)
        return text.trim()
    }

    fun decodePacket(
        ctx: AVCodecContext,
        stream: AVStream,
        pkt: AVPacket,
        sub: AVSubtitle,
        gotSub: IntPointer,
        list: MutableList<Cue>
    ) {
        if (avcodec_decode_subtitle2(ctx, sub, gotSub, pkt) < 0 || gotSub.get() == 0) return
        val tb = stream.time_base()
        val baseMs = if (sub.pts() != AV_NOPTS_VALUE) {
            sub.pts() / 1000L
        } else {
            (pkt.pts() * tb.num() * 1000L) / tb.den()
        }
        val startMs = baseMs + sub.start_display_time()
        var endMs = baseMs + sub.end_display_time()
        if (endMs <= startMs && pkt.duration() > 0) {
            endMs = startMs + (pkt.duration() * tb.num() * 1000L) / tb.den()
        }

        val sb = StringBuilder()
        for (i in 0 until sub.num_rects()) {
            val rect = sub.rects(i) ?: continue
            val type = rect.type()
            val text = when (type) {
                SUBTITLE_TEXT -> rect.text()?.getString()
                SUBTITLE_ASS -> rect.ass()?.getString()
                else -> null
            }
            if (text != null) {
                val clean = cleanText(text, isAss = (type == SUBTITLE_ASS))
                if (clean.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(clean)
                }
            }
        }
        if (sb.isNotEmpty()) {
            list.add(Cue(startMs = startMs, endMs = endMs, text = sb.toString()))
        }
        avsubtitle_free(sub)
    }

    private fun extractAssText(line: String): String {
        val match = assPrefixRegex.find(line)
        if (match != null) {
            return line.substring(match.range.last + 1)
        }
        var commaCount = 0
        var idx = 0
        while (idx < line.length && commaCount < 9) {
            if (line[idx] == ',') commaCount++
            idx++
        }
        return if (commaCount == 9 && idx < line.length) {
            line.substring(idx)
        } else {
            line
        }
    }

    private fun stripTags(input: String): String {
        val sb = StringBuilder(input.length)
        var inTag = false
        for (ch in input) {
            when {
                ch == '{' -> inTag = true
                ch == '}' -> inTag = false
                !inTag -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
