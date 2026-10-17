package com.woka.core.data

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
        watchRecords = watchRecords.filterKeys { !it.startsWith(prefix) }
    )
}

/**
 * Undoes [withoutFolder] for [path] from [before], the record as it stood just ahead
 * of the unlink (SIDE-010). Only what the unlink swept comes back — the link at its
 * old place in the list, the pin on the root, and the records under its prefix — and
 * nothing written since is overwritten: a key the current record already holds keeps
 * its current value. Undo within seconds of a volume change must not undo the volume.
 */
internal fun WokaSettings.withFolderRestored(
    before: WokaSettings,
    path: String,
    separator: Char
): WokaSettings {
    val prefix = path.trimEnd(separator) + separator
    val under = { key: String -> key == path || key.startsWith(prefix) }
    val links = if (path in linkedFolders) linkedFolders else {
        val at = before.linkedFolders.indexOf(path).coerceIn(0, linkedFolders.size)
        linkedFolders.take(at) + path + linkedFolders.drop(at)
    }
    val pins = pinnedSessions + before.pinnedSessions.filter { pin ->
        pin.path == path && pinnedSessions.none { it.path == path }
    }
    return copy(
        linkedFolders = links,
        pinnedSessions = pins,
        trackPrefsPerFile = before.trackPrefsPerFile.filterKeys(under) + trackPrefsPerFile,
        trackPrefsPerFolder = before.trackPrefsPerFolder.filterKeys(under) + trackPrefsPerFolder,
        watchRecords = before.watchRecords.filterKeys(under) + watchRecords
    )
}

/**
 * Writes the play head without disturbing [WatchRecord.finished].
 *
 * Rewinding to the start clears the position and **keeps** the watched mark: after
 * finishing a file, `resumePositionMs` returns null, so re-opening it starts at 0
 * and the saver immediately writes a position under the resume floor. Replacing the
 * whole record there would erase the mark the moment the file was opened again.
 */
internal fun WokaSettings.withWatchPosition(
    path: String,
    positionMs: Long,
    durationMs: Long
): WokaSettings {
    val existing = watchRecords[path] ?: WatchRecord()
    val updated = existing.copy(
        positionMs = positionMs,
        durationMs = durationMs.takeIf { it > 0L } ?: existing.durationMs,
        // Watching on past the resume floor means it is in progress again, whatever
        // it was before.
        finished = if (positionMs > 0L) false else existing.finished
    )
    return copy(watchRecords = watchRecords + (path to updated))
}

/** Marks a file watched — by playing it to the end, or by hand from the grid. */
internal fun WokaSettings.withWatchFinished(path: String, durationMs: Long): WokaSettings {
    val existing = watchRecords[path] ?: WatchRecord()
    return copy(
        watchRecords = watchRecords + (
            path to existing.copy(
                positionMs = 0L,
                durationMs = durationMs.takeIf { it > 0L } ?: existing.durationMs,
                finished = true
            )
            )
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

/**
 * Enabling a tool **removes** its id; disabling adds it (§3.4). Enabling one that
 * was never disabled is therefore a no-op that still writes an equal record — the
 * caller's `update` schedules a save either way, which is cheaper than teaching
 * every setter to detect a no-op.
 */
internal fun WokaSettings.withEditorTool(id: String, enabled: Boolean): WokaSettings =
    copy(
        editorDisabledTools =
            if (enabled) editorDisabledTools - id else editorDisabledTools + id
    )
