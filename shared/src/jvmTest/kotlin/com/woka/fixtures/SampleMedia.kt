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
