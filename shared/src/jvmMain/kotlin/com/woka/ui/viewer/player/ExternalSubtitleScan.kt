package com.woka.ui.viewer.player

import java.io.File

/** Extensions treated as text sidecars. Bitmap sidecars (`.idx`/`.sub`, `.sup`) are Step 10. */
private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa")

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
        format = file.extension.uppercase(),
        title = suffix.takeIf { it.isNotBlank() }
    )
}
