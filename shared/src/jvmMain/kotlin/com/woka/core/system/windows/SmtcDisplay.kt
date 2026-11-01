package com.woka.core.system.windows

import com.woka.core.system.MediaSessionInfo
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT

// ISystemMediaTransportControlsDisplayUpdater, after IInspectable's six.
private const val PUT_TYPE = 7
private const val PUT_THUMBNAIL = 11
private const val GET_MUSIC_PROPERTIES = 12
private const val GET_VIDEO_PROPERTIES = 13
private const val CLEAR_ALL = 16
private const val UPDATE = 17

// IMusicDisplayProperties: title, album artist, artist.
private const val MUSIC_PUT_TITLE = 7
private const val MUSIC_PUT_ARTIST = 11

// IVideoDisplayProperties: title, subtitle.
private const val VIDEO_PUT_TITLE = 7
private const val VIDEO_PUT_SUBTITLE = 9

// MediaPlaybackType.
private const val TYPE_MUSIC = 1
private const val TYPE_VIDEO = 2

/**
 * What the Windows media panel shows for the session (AP-007): a track as music — title
 * and artist — a film as video, and the cover or poster either way. Each call starts
 * from a cleared updater, so nothing of the previous source survives into the next.
 */
internal object SmtcDisplay {

    fun show(smtc: MemorySegment, getUpdaterSlot: Int, info: MediaSessionInfo) = Arena.ofConfined().use { arena ->
        val out = arena.allocate(ADDRESS)
        WinRt.callChecked(smtc, getUpdaterSlot, "get_DisplayUpdater", listOf(ADDRESS), out)
        val updater = out.get(ADDRESS, 0)
        try {
            WinRt.call(updater, CLEAR_ALL, FunctionDescriptor.of(JAVA_INT))
            val type = if (info.isVideo) TYPE_VIDEO else TYPE_MUSIC
            WinRt.callChecked(updater, PUT_TYPE, "put_Type", listOf(JAVA_INT), type)
            if (info.isVideo) {
                writeText(
                    updater, GET_VIDEO_PROPERTIES,
                    VIDEO_PUT_TITLE to info.title, VIDEO_PUT_SUBTITLE to info.artist
                )
            } else {
                writeText(
                    updater, GET_MUSIC_PROPERTIES,
                    MUSIC_PUT_TITLE to info.title, MUSIC_PUT_ARTIST to info.artist
                )
            }
            info.cover?.let { putThumbnail(updater, it) }
            WinRt.callChecked(updater, UPDATE, "Update", emptyList())
        } finally {
            WinRt.release(updater)
        }
    }

    /** Takes the properties object from [getSlot] and writes each `slot to text` into it. */
    private fun writeText(updater: MemorySegment, getSlot: Int, vararg fields: Pair<Int, String>) =
        Arena.ofConfined().use { arena ->
            val out = arena.allocate(ADDRESS)
            WinRt.callChecked(updater, getSlot, "get properties", listOf(ADDRESS), out)
            val properties = out.get(ADDRESS, 0)
            try {
                for ((slot, text) in fields) {
                    if (text.isBlank()) continue
                    WinRt.withHString(text) { hstring ->
                        WinRt.callChecked(properties, slot, "put text", listOf(ADDRESS), hstring)
                    }
                }
            } finally {
                WinRt.release(properties)
            }
        }

    /** A cover that will not go through leaves the panel's placeholder, not a failed update. */
    private fun putThumbnail(updater: MemorySegment, png: ByteArray) {
        try {
            val reference = SmtcThumbnail.reference(png)
            try {
                WinRt.callChecked(updater, PUT_THUMBNAIL, "put_Thumbnail", listOf(ADDRESS), reference)
            } finally {
                WinRt.release(reference)
            }
        } catch (e: IllegalStateException) {
            System.err.println("SMTC thumbnail skipped: ${e.message}")
        }
    }
}
