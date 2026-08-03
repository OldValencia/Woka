package com.woka.ui.viewer.player

/**
 * A sidecar subtitle file sitting next to the video (Step 9). Deliberately *not*
 * a [MediaTrack]: `MediaTracks` is what the container holds, and the File Info
 * dialog (§2.5) prints it as the stream list — folding sidecars in there would
 * make that dialog lie.
 */
data class ExternalSubtitle(
    val path: String,
    val fileName: String,
    val language: String? = null,
    val forced: Boolean = false,
    val sdh: Boolean = false,
    val format: String = "SRT",
    /** Fallback name when the file carries no language token. */
    val title: String? = null,
    /**
     * Added through the picker rather than found next to the video. These are the
     * ones the folder scan cannot find again — they may live anywhere and be named
     * anything — so they are the ones written to `TrackPrefs.subtitleExternalAdded`.
     * It carries no meaning in the UI: every sidecar row behaves the same.
     */
    val isManual: Boolean = false
) {
    /**
     * `English · forced` / `Russian` / `Subtitle file`. The plain `movie.srt` case
     * — the commonest one — has nothing to parse, and its file name is the video's
     * full release name, far too long for the popover, so it gets a generic label.
     */
    val label: String
        get() {
            val flags = listOfNotNull(
                "forced".takeIf { forced },
                "SDH".takeIf { sdh }
            )
            val base = language?.let { languageName(it) } ?: title ?: "Subtitle file"
            return if (flags.isEmpty()) base else "$base · ${flags.joinToString(" · ")}"
        }
}

private val LANGUAGE_NAMES = mapOf(
    "en" to "English", "eng" to "English",
    "ru" to "Russian", "rus" to "Russian",
    "uk" to "Ukrainian", "ukr" to "Ukrainian",
    "de" to "German", "ger" to "German", "deu" to "German",
    "fr" to "French", "fre" to "French", "fra" to "French",
    "es" to "Spanish", "spa" to "Spanish",
    "it" to "Italian", "ita" to "Italian",
    "pl" to "Polish", "pol" to "Polish",
    "ja" to "Japanese", "jpn" to "Japanese",
    "zh" to "Chinese", "chi" to "Chinese", "zho" to "Chinese",
    "ko" to "Korean", "kor" to "Korean",
    "pt" to "Portuguese", "por" to "Portuguese",
    "tr" to "Turkish", "tur" to "Turkish",
    "ar" to "Arabic", "ara" to "Arabic"
)

/** Display name for an ISO 639 code, or the code itself upper-cased. */
fun languageName(code: String): String =
    LANGUAGE_NAMES[code.lowercase()] ?: code.uppercase()
