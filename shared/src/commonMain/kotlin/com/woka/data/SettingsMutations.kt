package com.woka.data

/**
 * The non-trivial `WokaSettings → WokaSettings` transforms, as pure functions.
 *
 * Split out of `SettingsRepositoryImpl` per the §2 file-size mandate: interface
 * overrides cannot leave the class, but their *bodies* can, which turns every
 * multi-line setter there into a one-liner — the same move that sent the disk
 * layer to `SettingsStore` in Step 5.
 */

/**
 * Unlinking must leave nothing behind (§2.6 cleanup): the folder's own records,
 * every per-file record under it, and every remembered playback position under it
 * (§4). A path prefix is what makes that answerable, which is why these maps are
 * keyed by absolute path rather than by a content hash.
 */
internal fun WokaSettings.withoutFolder(path: String, separator: Char): WokaSettings {
    val prefix = path.trimEnd(separator) + separator
    return copy(
        linkedFolders = linkedFolders - path,
        trackPrefsPerFile = trackPrefsPerFile.filterKeys { !it.startsWith(prefix) },
        trackPrefsPerFolder = trackPrefsPerFolder
            .filterKeys { it != path && !it.startsWith(prefix) },
        playbackPositions = playbackPositions.filterKeys { !it.startsWith(prefix) }
    )
}

/**
 * Both layers are written on every manual track switch: the per-file record is
 * authoritative when this exact file is reopened, the per-folder one seeds the
 * next episode in the same folder.
 *
 * The two records are built by the caller and are deliberately *not* the same
 * object (Step 15). The file's is a merge of everything ever chosen in that file;
 * the folder's carries only what this action chose, so an untouched dimension
 * cannot silently revert the folder to a value some other file set long ago.
 */
internal fun WokaSettings.withTrackPrefs(
    filePath: String,
    folderPath: String,
    filePrefs: TrackPrefs,
    folderPrefs: TrackPrefs
): WokaSettings = copy(
    trackPrefsPerFile = trackPrefsPerFile + (filePath to filePrefs),
    trackPrefsPerFolder = trackPrefsPerFolder + (folderPath to folderPrefs)
)

/** Subtitle appearance (§3) back to the shipped defaults. */
internal fun WokaSettings.withDefaultSubtitleAppearance(): WokaSettings {
    val defaults = WokaSettings()
    return copy(
        subtitleSize = defaults.subtitleSize,
        subtitleFont = defaults.subtitleFont,
        subtitleColorArgb = defaults.subtitleColorArgb,
        subtitleBoxOpacity = defaults.subtitleBoxOpacity,
        subtitleOutline = defaults.subtitleOutline
    )
}

/** A pinned session replaces any earlier one for the same folder. */
internal fun WokaSettings.withPinnedSession(session: PinnedSession): WokaSettings =
    copy(pinnedSessions = pinnedSessions.filter { it.path != session.path } + session)
