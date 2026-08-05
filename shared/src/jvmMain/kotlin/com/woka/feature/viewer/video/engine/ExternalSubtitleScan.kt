package com.woka.feature.viewer.video.engine

import java.io.File
import com.woka.feature.viewer.video.model.ExternalSubtitle

private val TEXT_SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa")

/**
 * Bitmap sidecars (Step 10). `.sub` is deliberately absent: it is the payload of
 * the `.idx` beside it — FFmpeg's `vobsub` demuxer is pointed at the index and
 * finds it itself — and a bare `.sub` is also the MicroDVD text format, so listing
 * it would add a second row for the same subtitles that decodes as neither.
 */
private val BITMAP_SUBTITLE_EXTENSIONS = setOf("sup", "idx")

private val SUBTITLE_EXTENSIONS = TEXT_SUBTITLE_EXTENSIONS + BITMAP_SUBTITLE_EXTENSIONS

/** Everything the "Add subtitle file…" picker accepts (§2.2). */
val SUBTITLE_FILE_EXTENSIONS: List<String> = SUBTITLE_EXTENSIONS.toList()

/** True when this sidecar decodes to pictures rather than text (Step 10). */
fun isBitmapSubtitleFile(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in BITMAP_SUBTITLE_EXTENSIONS

private val FLAG_TOKENS = setOf("forced", "sdh", "cc", "hi")

/**
 * Sidecars belonging to [video]: every sibling whose name starts with the video's
 * base name and ends in a text-subtitle extension. Scanning the whole set rather
 * than only the exact-name match is what makes `movie.ru.srt` / `movie.eng.forced.srt`
 * — the normal shape of a fansub or a separately-pulled translation — show up as
 * distinct, labelled entries instead of being invisible.
 */
fun scanExternalSubtitles(video: File): List<ExternalSubtitle> {
    val folder = video.parentFile ?: return emptyList()
    val base = video.nameWithoutExtension
    val siblings = folder.listFiles() ?: return emptyList()
    return siblings
        .filter { it.isFile && it.extension.lowercase() in SUBTITLE_EXTENSIONS }
        .filter { it.nameWithoutExtension.startsWith(base, ignoreCase = true) }
        .map { describeSidecar(it, base) }
        .sortedWith(compareBy({ it.language == null }, { it.label }))
}

/**
 * Builds an [ExternalSubtitle] for an arbitrary file picked by hand. There is no
 * video base name to subtract, so the whole stem is the title and any language
 * token in it is still picked up.
 */
fun describePickedSubtitle(file: File): ExternalSubtitle =
    describeSidecar(file, base = "").copy(title = file.name, isManual = true)

/**
 * Reads the suffix chain between the video's base name and the extension:
 * `movie.eng.forced.srt` → language `eng`, forced. Tokens that are neither a
 * 2–3 letter code nor a known flag are ignored rather than guessed at.
 */
private fun describeSidecar(file: File, base: String): ExternalSubtitle {
    val stem = file.nameWithoutExtension
    val suffix = if (base.isNotEmpty() && stem.length > base.length) {
        stem.substring(base.length).trim('.', '_', '-', ' ')
    } else {
        ""
    }
    val tokens = suffix.split('.', '_', '-', ' ').filter { it.isNotBlank() }
    var language: String? = null
    var forced = false
    var sdh = false
    for (token in tokens) {
        val lower = token.lowercase()
        when {
            lower in FLAG_TOKENS -> {
                if (lower == "forced") forced = true else sdh = true
            }
            language == null && lower.length in 2..3 && lower.all { it.isLetter() } -> {
                language = lower
            }
        }
    }
    return ExternalSubtitle(
        path = file.absolutePath,
        fileName = file.name,
        language = language,
        forced = forced,
        sdh = sdh,
        format = formatLabel(file.extension),
        title = suffix.takeIf { it.isNotBlank() }
    )
}

/** What the format is called on screen, which is not always its extension. */
private fun formatLabel(extension: String): String = when (extension.lowercase()) {
    "sup" -> "PGS"
    "idx" -> "VobSub"
    else -> extension.uppercase()
}
