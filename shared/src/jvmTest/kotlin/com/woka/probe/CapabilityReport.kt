package com.woka.probe

/**
 * Formats the probe's findings as a Markdown table, so the output can be pasted
 * into `rare-image-formats-and-frame-sets.md` unedited — Phase 0 asks for the
 * exact probe output recorded beside the routing table it produces, and a
 * transcription is not that.
 */
internal fun renderTable(headers: List<String>, rows: List<List<String>>): String {
    val widths = headers.indices.map { column ->
        (rows.map { it[column] } + headers[column]).maxOf { it.length }
    }
    val lines = mutableListOf(line(headers, widths), divider(widths))
    rows.forEach { lines += line(it, widths) }
    return lines.joinToString("\n")
}

private fun line(cells: List<String>, widths: List<Int>): String =
    cells.mapIndexed { index, cell -> cell.padEnd(widths[index]) }
        .joinToString(" | ", prefix = "| ", postfix = " |")

private fun divider(widths: List<Int>): String =
    widths.joinToString("-|-", prefix = "|-", postfix = "-|") { "-".repeat(it) }

/** A pipe inside a cell would break the table it is being written into. */
private fun String.cell(): String = replace('|', '/')

internal fun skiaCodecCell(report: SkiaReport): String = when {
    !report.opened -> "**✗** ${report.failure}".cell()
    report.durationsMs.any { it > 0 } ->
        "${report.frameCount} frames, ${report.size}, ${report.durationsMs} ms"
    else -> "${report.frameCount} frames, ${report.size}"
}

/** The viewer's own call today, asked separately from the codec that counts frames. */
internal fun skiaImageCell(report: SkiaReport): String =
    if (report.imageFailure == null) "ok" else "**✗** ${report.imageFailure}".cell()

internal fun imageIoCell(report: ImageIoReport): String = when {
    report.failure == null -> "${report.reader}, ${report.imageCount} images, ${report.size}"
    report.reader == "—" -> "**✗** ${report.failure}".cell()
    else -> "**✗** `${report.reader}` — ${report.failure}".cell()
}

/**
 * Streams **and** frames, because for a HEIF container they are different
 * questions: a five-shot burst is five one-frame video streams, and the grabber
 * only ever decodes one of them.
 */
internal fun ffmpegCell(report: FfmpegReport): String = when {
    !report.opened -> "**✗** ${report.failure}".cell()
    else -> "${report.codec}, **${report.videoStreams} video streams**, " +
        "${report.grabbedFrames} frames, ${report.size}, ${report.pixelFormat}"
}

/**
 * The two answers side by side. Phase 5 cannot pick a path for a still's
 * transfer function until it knows which of them carries one.
 */
internal fun colourCell(skia: SkiaReport, ffmpeg: FfmpegReport): String {
    val fromSkia = if (skia.opened) skia.colorSpace else "—"
    val hdr = ffmpeg.hdr
    val fromFfmpeg = when {
        hdr == null -> "—"
        hdr.isWideGamut -> "${hdr.transfer} + wide"
        else -> "${hdr.transfer}"
    }
    return "skia `$fromSkia` · ffmpeg `$fromFfmpeg`"
}

internal fun presenceCell(present: Boolean): String = if (present) "**yes**" else "**no**"
