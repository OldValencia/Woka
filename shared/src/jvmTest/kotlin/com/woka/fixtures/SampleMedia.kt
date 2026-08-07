package com.woka.fixtures

import java.io.File

private const val RESOURCE_DIR = "/media"

/**
 * Release-shaped stem: dots in the name and a language suffix are exactly what
 * the external-subtitle scan has to cope with.
 */
private const val EPISODE_STEM = "WokaSample.S01E%02d.2160p.HDR.TEST"

/** h264 plus two bitmap subtitle tracks: `hdmv_pgs_subtitle` and `dvd_subtitle`. */
const val BITMAP_SUBS = "WokaSample.BitmapSubs.mkv"

/** Two video streams — 10-bit PQ/BT.2020 and 8-bit BT.709 — two audio, two subtitle. */
const val DUAL_VIDEO = "WokaSample.DualVideo.mkv"

/**
 * Two `subrip` tracks that actually carry packets.
 *
 * Added in Phase 3: every other container here was cut to three or four seconds,
 * which sliced out every subtitle event — `S01E01` and `DualVideo` still *declare*
 * their `subrip` tracks, and a probe finds them, but a demux pass yields nothing.
 * Text subtitles are the one thing ffmpeg will happily mux, so the gap is closed
 * with a real file rather than left as an untested path.
 */
const val TEXT_SUBS = "textsubs.mkv"

/** Three chapters at 0–0.8, 0.8–2.0, 2.0–3.0 seconds. */
const val CHAPTERS = "chapters.mkv"

/** Video with no audio stream at all. */
const val NO_AUDIO = "noaudio.mkv"

/** The first 2 KB of a valid container: opens, then runs out. */
const val BROKEN = "broken.mkv"

const val STILL_PNG = "still.png"
const val STILL_JPG = "still.jpg"
const val STILL_WEBP = "still.webp"

/** The same image as [STILL_JPG] with EXIF `Orientation=6` spliced in. */
const val EXIF_ROTATED = "exif_rot90.jpg"

const val STILL_WIDTH = 640
const val STILL_HEIGHT = 480

/**
 * Frame sets: one file, more than one picture inside it.
 *
 * Every plate carries its own index painted across it, so a strip that shows
 * the wrong frame is wrong at a glance. Counts are asserted, not guessed —
 * [BURST_FRAME_COUNT] and friends are what the generator wrote.
 */
const val BURST_HEIC = "burst.heic"
const val ANIM_GIF = "anim.gif"
const val ANIM_WEBP = "anim.webp"
const val MULTIPAGE_TIFF = "multipage.tif"

const val BURST_FRAME_COUNT = 5
const val ANIM_FRAME_COUNT = 8
const val TIFF_PAGE_COUNT = 3
const val FRAME_WIDTH = 320
const val FRAME_HEIGHT = 240

/**
 * A Live Photo is a **pair on disk**, not one file — Apple exports the still and
 * its motion clip side by side, sharing a stem. Whatever finds the motion frame
 * has to look at the directory, not inside the container.
 */
const val LIVE_PHOTO_STILL = "livephoto.heic"
const val LIVE_PHOTO_MOTION = "livephoto.mov"

/** Single-frame AVIF, SDR: the format is the question, not the colour. */
const val STILL_AVIF = "still.avif"

/** HDR stills. `hdr_pq.png` exists so the skia path has one even if AVIF fails. */
const val HDR_PQ_AVIF = "hdr_pq.avif"
const val HDR_HLG_AVIF = "hdr_hlg.avif"
const val HDR_PQ_PNG = "hdr_pq.png"

/** A loader that honours the tag turns the 640×480 source portrait. */
const val EXIF_EXPECTED_WIDTH = 480
const val EXIF_EXPECTED_HEIGHT = 640

/**
 * The media corpus, committed under `jvmTest/resources/media`.
 *
 * It is checked in rather than generated. `lavfi` cannot produce bitmap subtitle
 * tracks — ffmpeg refuses text-to-bitmap transcoding outright — and synthesising
 * the rest would only approximate what real files look like: seven audio tracks
 * sharing one language and differing only by title, sidecars named the way real
 * releases name them, genuine PQ/BT.2020 colour metadata. Provenance and the
 * exact commands are in `resources/media/README.md`.
 *
 * Everything here ships with the repository, so a missing file is a broken
 * checkout and fails loudly instead of quietly skipping.
 */
object SampleMedia {

    val root: File by lazy {
        val url = javaClass.getResource(RESOURCE_DIR)
            ?: error("$RESOURCE_DIR is missing from jvmTest resources")
        File(url.toURI())
    }

    /** Name of an episode file; [suffix] is `.mkv`, `.srt`, `.ru.srt` and so on. */
    fun episode(number: Int, suffix: String): String = EPISODE_STEM.format(number) + suffix

    fun file(name: String): File {
        val target = File(root, name)
        check(target.isFile) { "test media '$name' is missing from $root" }
        return target
    }
}
