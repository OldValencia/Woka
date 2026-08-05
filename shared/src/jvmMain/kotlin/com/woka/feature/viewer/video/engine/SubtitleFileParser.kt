package com.woka.feature.viewer.video.engine

import java.io.File
import com.woka.feature.viewer.video.model.Cue

/**
 * Parses a sidecar `.srt` / `.ass` / `.ssa` into the same [Cue] list embedded
 * tracks produce (Step 9), so the overlay, the §3 styling and the sync offset all
 * apply unchanged. Text cleanup is delegated to [SubtitleParser.cleanText], which
 * already strips ASS dialogue prefixes, `{…}` override tags and normalises `\N`.
 */
fun parseSubtitleFile(file: File): List<Cue> = try {
    val text = readSubtitleText(file)
    when (file.extension.lowercase()) {
        "ass", "ssa" -> parseAss(text)
        else -> parseSrt(text)
    }
} catch (e: Exception) {
    e.printStackTrace()
    emptyList()
}

private val SRT_TIME = Regex(
    """(\d+):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d+):(\d{2}):(\d{2})[,.](\d{1,3})"""
)

/** Blocks separated by a blank line: index, `00:00:01,000 --> 00:00:06,000`, text. */
internal fun parseSrt(text: String): List<Cue> {
    val cues = mutableListOf<Cue>()
    for (block in text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n{2,}"))) {
        val lines = block.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) continue
        val timeLineIndex = lines.indexOfFirst { it.contains("-->") }
        if (timeLineIndex < 0) continue
        val match = SRT_TIME.find(lines[timeLineIndex]) ?: continue
        val body = lines.drop(timeLineIndex + 1).joinToString("\n")
        val clean = SubtitleParser.cleanText(body)
        if (clean.isEmpty()) continue
        cues += Cue(
            startMs = srtTimeMs(match.groupValues, 1),
            endMs = srtTimeMs(match.groupValues, 5),
            text = clean
        )
    }
    return cues
}

private fun srtTimeMs(groups: List<String>, from: Int): Long {
    val hours = groups[from].toLong()
    val minutes = groups[from + 1].toLong()
    val seconds = groups[from + 2].toLong()
    // A 1- or 2-digit fraction means tenths/hundredths, not milliseconds.
    val fraction = groups[from + 3]
    val millis = fraction.padEnd(3, '0').take(3).toLong()
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}

/** `Dialogue: 0,0:00:01.00,0:00:06.00,Style,,0,0,0,,Text` — 9 fields, then the text. */
internal fun parseAss(text: String): List<Cue> {
    val cues = mutableListOf<Cue>()
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
        val fields = line.substringAfter(':').split(',', limit = 10)
        if (fields.size < 10) continue
        val startMs = assTimeMs(fields[1].trim()) ?: continue
        val endMs = assTimeMs(fields[2].trim()) ?: continue
        val clean = SubtitleParser.cleanText(fields[9], isAss = true)
        if (clean.isEmpty()) continue
        cues += Cue(startMs = startMs, endMs = endMs, text = clean)
    }
    return cues
}

/** `H:MM:SS.cc` — hours are not zero-padded and the fraction is centiseconds. */
private fun assTimeMs(value: String): Long? {
    val parts = value.split(':')
    if (parts.size != 3) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val secondParts = parts[2].split('.', ',')
    val seconds = secondParts[0].toLongOrNull() ?: return null
    val fraction = secondParts.getOrNull(1).orEmpty()
    val millis = fraction.padEnd(3, '0').take(3).toLong()
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}
