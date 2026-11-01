package com.woka.feature.viewer.shell

import com.woka.core.data.MediaItem
import com.woka.feature.viewer.audio.AudioTags
import com.woka.feature.viewer.video.model.MediaTrack
import com.woka.feature.viewer.video.model.MediaTracks
import java.text.SimpleDateFormat
import java.util.Date

/** Formatting helpers for the File Info stream sections (§2.5). */

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatFps(fps: Double): String {
    val rounded = (fps * 1000).toLong() / 1000.0
    val text =
        if (rounded % 1.0 == 0.0) rounded.toLong().toString()
        else "%.3f".format(rounded).trimEnd('0').trimEnd('.')
    return "$text fps"
}

internal fun formatBitrate(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
    bps >= 1_000 -> "${bps / 1_000} kbps"
    else -> "$bps bps"
}

/** 44100 → "44.1 kHz", 48000 → "48 kHz". */
internal fun formatSampleRate(hz: Int): String =
    "%.1f".format(hz / 1000.0).removeSuffix(".0").removeSuffix(",0") + " kHz"

internal fun subtitleFormat(codec: String): String = when (codec.lowercase()) {
    "subrip" -> "SRT"
    "ass", "ssa" -> "ASS"
    "mov_text" -> "Text"
    "webvtt" -> "WebVTT"
    "hdmv_pgs_subtitle" -> "PGS"
    "dvd_subtitle" -> "VobSub"
    "dvb_subtitle" -> "DVB"
    else -> codec.uppercase()
}

internal fun MediaTrack.resolution(): String? =
    if (width != null && height != null) "${width}×${height}" else null

/** [full] for a file of sound alone (AP-003): there the rate and bitrate are the point. */
internal fun audioDetail(a: MediaTrack, full: Boolean = false): String = listOfNotNull(
    a.codec.uppercase(),
    a.channelLayout,
    a.sampleRate?.takeIf { full }?.let(::formatSampleRate),
    a.bitRate?.takeIf { full }?.let(::formatBitrate)
).joinToString(" · ")

internal fun subtitleDetail(s: MediaTrack): String =
    listOfNotNull(subtitleFormat(s.codec), s.language).joinToString(" · ")

internal fun Long.toReadableSize(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "${this / 1024} KB"
    this < 1024 * 1024 * 1024 -> "%.2f MB".format(this / (1024.0 * 1024.0))
    else -> "%.2f GB".format(this / (1024.0 * 1024.0 * 1024.0))
}

/** Full plaintext of the dialog, for the Copy button (reliable "copy all"). */
internal fun buildInfoText(
    item: MediaItem,
    tracks: MediaTracks?,
    durationMs: Long?,
    tags: AudioTags? = null
): String = buildString {
    appendLine("Name: ${item.file.name}")
    appendLine("Path: ${item.file.absolutePath}")
    appendLine("Size: ${item.file.length().toReadableSize()}")
    appendLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(item.file.lastModified()))}")
    tags?.let { trackTagRows(it).forEach { (label, value) -> appendLine("$label: $value") } }
    if (tracks == null) return@buildString
    val videos = tracks.video
    if (videos.size == 1) videos[0].resolution()?.let { appendLine("Resolution: $it") }
    durationMs?.takeIf { it > 0 }?.let { appendLine("Duration: ${formatDuration(it)}") }
    videos.forEach { v ->
        val prefix = if (videos.size > 1) "Video (${v.label})" else "Video"
        val parts = listOfNotNull(
            v.codec.uppercase(),
            v.resolution().takeIf { videos.size > 1 },
            v.frameRate?.let(::formatFps),
            v.bitRate?.let(::formatBitrate)
        )
        appendLine("$prefix: ${parts.joinToString(", ")}")
    }
    tracks.audio.forEach { a -> appendLine("Audio: ${a.label} — ${audioDetail(a, full = videos.isEmpty())}") }
    tracks.subtitle.forEach { s -> appendLine("Subtitle: ${s.label} — ${subtitleDetail(s)}") }
}.trimEnd()
