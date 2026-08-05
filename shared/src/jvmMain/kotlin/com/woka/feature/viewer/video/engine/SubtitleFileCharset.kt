package com.woka.feature.viewer.video.engine

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private val CP1251: Charset = Charset.forName("windows-1251")

/**
 * Reads a sidecar subtitle file as text, deciding the encoding ourselves.
 *
 * This is the reason external subtitles are parsed in Kotlin rather than handed to
 * FFmpeg: `sub_charenc` only works when FFmpeg was built with iconv, and when it
 * was not the option fails silently — the result is mojibake that reads exactly
 * like a broken parser. Reading the bytes here makes the decision explicit.
 *
 * Order: BOM if present, then a **strict** UTF-8 decode (malformed input throws
 * rather than producing replacement characters), then CP1251. The single-byte
 * fallback is CP1251 rather than CP1252 deliberately: this project's corpus is
 * Russian, and the two are indistinguishable without language detection.
 */
fun readSubtitleText(file: File): String {
    val bytes = file.readBytes()
    if (bytes.isEmpty()) return ""

    bomCharset(bytes)?.let { (charset, offset) ->
        return String(bytes, offset, bytes.size - offset, charset)
    }
    decodeStrictly(bytes)?.let { return it }
    return String(bytes, CP1251)
}

/** The declared charset and the length of the mark, when the file carries a BOM. */
private fun bomCharset(bytes: ByteArray): Pair<Charset, Int>? {
    fun at(i: Int): Int = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
    return when {
        at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF -> StandardCharsets.UTF_8 to 3
        at(0) == 0xFF && at(1) == 0xFE -> StandardCharsets.UTF_16LE to 2
        at(0) == 0xFE && at(1) == 0xFF -> StandardCharsets.UTF_16BE to 2
        else -> null
    }
}

/** Decodes or returns null — never substitutes, so a wrong guess is detectable. */
private fun decodeStrictly(bytes: ByteArray): String? = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
}
