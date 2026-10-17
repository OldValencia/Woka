package com.woka.core.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

enum class ViewLayout { SmallGrid, Grid, BigGrid }
enum class SortType { Custom, NameAsc, NameDesc, DateAsc, DateDesc }
enum class CarouselVisibility { AUTO, ALWAYS_ON, HIDDEN }
enum class ScalePreset { Fit, Fill, Original }
// Subtitle appearance (§3). Auto scales the size from the video-panel height; the
// other steps are fixed multipliers of that same relative base, so the user's
// choice stays proportional on window resize instead of being reset.
enum class SubtitleSize { Auto, Small, Medium, Large, ExtraLarge }
enum class SubtitleFont { System, Serif, Monospace }

/**
 * The size-state schema this build writes. The field below still **defaults to 0**
 * on purpose: that default is what a `settings.json` written before the field
 * existed decodes to, and it is the only signal that such a file is genuinely old.
 * A state with no file behind it is not old, so `SettingsRepositoryImpl` starts
 * from this constant instead — otherwise the very first session's size defaults
 * were migrated away on the next launch (F8).
 */
const val SIZE_SCHEMA_VERSION = 1

@Immutable
@Serializable
data class PinnedSession(
    val path: String,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)

@Immutable
@Serializable
data class WokaSettings(
    val linkedFolders: List<String> = emptyList(),
    val folderLayouts: Map<String, ViewLayout> = emptyMap(),
    val folderSorts: Map<String, SortType> = emptyMap(),
    val folderShowTitles: Map<String, Boolean> = emptyMap(),
    val showTitles: Boolean = true,
    val showFolders: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val mediaFilter: String = "All", // All, Photos, Videos
    val theme: String = "System",
    val mediaViewerCarouselMode: CarouselVisibility = CarouselVisibility.AUTO,
    // Fills the empty *side* bars of a fullscreen video with a blurred, dimmed
    // derivative of the picture. Side bars only: a 16:9 file on a 21:9 panel is a
    // monitor mismatch, while the top/bottom letterbox of 2.39:1 content is how it
    // was graded and stays black. Off by default — it is a taste feature and it
    // changes every fullscreen video.
    val ambientFill: Boolean = false,
    val pinnedSessions: List<PinnedSession> = emptyList(),
    // The folder on screen and its scroll, which is what launch reopens (SIDE-002).
    // Written as the folder changes and as its scroll settles, never at exit. Null
    // until a folder has been shown; honoured only while inside a linked root.
    val lastSession: PinnedSession? = null,
    val scalePreset: ScalePreset = ScalePreset.Fit,
    /** Overrides HDR *detection*, never the output — see [HdrViewMode] (ME-119). */
    val hdrViewMode: HdrViewMode = HdrViewMode.Auto,
    // Default size state applied when a video opens. If [rememberSizeState] is on,
    // adjusting zoom/preset inside a video writes back into these fields.
    // [defaultZoomPercent] is a percentage of native 1:1 pixels (Step 8b);
    // [sizeSchemaVersion] guards the one-off reset of records written under the
    // old percent-of-fitted base, which cannot be converted (the fitted size
    // depends on a panel that is not known when settings are read).
    val rememberSizeState: Boolean = false,
    val defaultUseZoom: Boolean = false,
    val defaultZoomPercent: Int = 100,
    val sizeSchemaVersion: Int = 0,
    // Last media-viewer volume, restored across files and app restarts. 0f–1f
    // ordinarily; up to [MAX_MEDIA_VOLUME] while [soundBoost] is on, and pulled
    // back to 1f the moment it is turned off.
    val mediaVolume: Float = 0.5f,
    // Lets the volume slider go past 100 % (Sound boost). Off by default: above
    // unity the samples are scaled and hard-clipped, which is a thing to opt into
    // rather than something every quiet file gets silently.
    val soundBoost: Boolean = false,
    // Subtitle appearance (§3), applied to text subtitles. Persisted globally.
    val subtitleSize: SubtitleSize = SubtitleSize.Auto,
    val subtitleFont: SubtitleFont = SubtitleFont.System,
    val subtitleColorArgb: Long = 0xFFFFFFFFL,
    val subtitleBoxOpacity: Float = 0.75f,
    val subtitleOutline: Boolean = true,
    // Remembered track choices (§2.6). Per absolute file path is authoritative;
    // the per-folder layer seeds the next episode of a series (audio + subtitles
    // only — a second video stream in another file is a different angle, not the
    // same track).
    val trackPrefsPerFile: Map<String, TrackPrefs> = emptyMap(),
    val trackPrefsPerFolder: Map<String, TrackPrefs> = emptyMap(),
    // Playback modes (§6), global rather than per-file: they are how the user
    // likes to watch, not a property of any one video.
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    val playbackSpeed: Float = PlaybackSpeed.NORMAL,
    // Playback resume (§4). Off by default: silently starting a file somewhere in
    // the middle is a surprise until the user has asked for it. Off records
    // nothing and restores nothing; positions already written stay in the file and
    // are swept by the folder-unlink / file-delete cleanup like the track records.
    val resumeMode: ResumeMode = ResumeMode.Off,
    val resumeMinDurationMinutes: Int = 20,
    // Where "watched" begins (§4). [WatchedWhen.MostOfIt] by default: the mark's
    // job is to say "you have seen this", and a film stopped over its closing
    // credits has been seen. The old flat tail is still there as
    // [WatchedWhen.TheVeryEnd] for anyone who means the literal last frame.
    val watchedWhen: WatchedWhen = WatchedWhen.MostOfIt,
    // Resume point plus watched state, per absolute path. One record rather than a
    // bare position, because the grid's marker needs a duration to divide by and a
    // "finished" that survives — see [WatchRecord].
    val watchRecords: Map<String, WatchRecord> = emptyMap(),
    // What the right-hand label beside the seek bar shows: the file's length, or
    // what is left of it. A Boolean rather than a two-value enum because the label
    // itself is a toggle — an enum would turn every read into "if it is Total then
    // Remaining else Total". Off by default: the total is what it has always said.
    val showRemainingTime: Boolean = false,
    // Fades the thumbnail of any video already started, so an untouched one stands
    // out. On by default: the marks are the feature, and a library where everything
    // looks identical is the state this exists to fix. Videos only — a photograph
    // has no such thing as partly seen.
    val dimWatchedVideos: Boolean = true,
    // The per-folder layer of §2.6 — guessing this episode's audio/subtitle tracks
    // from the last file watched in the same folder. Off by default: it is a guess,
    // and a wrong one silently changes the language a file opens in. The per-file
    // record is unaffected and always applies.
    val carryTracksAcrossFolder: Boolean = false,
    // OS media session (§4): hardware media keys plus the system Now Playing
    // panel. On by default — with the session registered the OS routes the keys
    // to whichever app is playing, so Woka never takes them from anything else.
    val systemMediaControls: Boolean = true,
    // Keeps the picture-in-picture window above other apps. On by default: a PiP
    // window that falls behind the app you just clicked is not doing the one job
    // it exists for. Applied only while the window is PiP-sized — over a window
    // that fills the screen it would pin the whole desktop.
    val pipAlwaysOnTop: Boolean = true,
    // Editor tools the user switched **off** (§3.4). A disabled-set rather than an
    // enabled-set so that a tool shipped in a later release appears for everyone:
    // an enabled-set written today would not name it, and every existing
    // `settings.json` would silently hide the new tool until the user found the
    // switch. The cost is that a removed tool leaves a dead id here, which the
    // registry ignores.
    val editorDisabledTools: Set<String> = emptySet()
)
