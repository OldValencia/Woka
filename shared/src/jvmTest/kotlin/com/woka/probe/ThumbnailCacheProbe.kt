package com.woka.probe

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.data.WokaStorage
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.utils.generateVideoThumbnail
import com.woka.core.utils.loadStillCover
import com.woka.feature.viewer.video.engine.isMonochrome
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.CHAPTERS
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.SampleMedia
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/** A plausible screenful of the Home grid. */
private const val TILES = 8

/** What a photograph off a phone actually measures. */
private const val PHOTO_WIDTH = 4_000
private const val PHOTO_HEIGHT = 3_000

/** What `StillPreview` and the poster extractor both scale to. */
private const val PREVIEW_MAX_PX = 400

/** Stand-ins for a `.thumbnails` left to grow, for the sweep timing. */
private const val SYNTHETIC_ENTRIES = 4_000

/**
 * What `.thumbnails` buys, and what bounding it would cost — the measurement
 * `pending/the-thumbnail-cache-grows-forever.md` asks for **before** either branch
 * of that ticket is taken.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.ThumbnailCacheProbe" -i
 *
 * A screenful is loaded twice per population: once with the directory emptied, once
 * with it holding an entry and the in-memory cache evicted, so only the disk layer is
 * under measurement. **A warm pass that quietly rebuilt its entry would report as a
 * slow cache hit rather than as no cache hit at all**, which is exactly what the videos
 * turned out to be doing — so [measure] plants a poster of a known length first and
 * counts the ones that no longer have it.
 *
 * That is how the video cache was found not to work at all: `VideoThumbnailExtractor`
 * used to re-run `isMonochrome` on the poster it had just read and go back to ffmpeg
 * when the answer was yes, so a flat frame meant a full extraction on every launch.
 * Both video rows now report no rebuilds; keeping both populations, one real frame and
 * one flat, is what stops that regressing quietly.
 *
 * The committed corpus tops out at 320×240, which says nothing about a photograph off
 * a phone — so [photoSizedStill] synthesises one at 4000×3000. That row is the one
 * that answers "could we simply not write the directory at all", and it is generous
 * to the no-cache side: a smooth PNG gradient is the cheapest thing there is to
 * decode, where a real HEIC of the same dimensions is slower.
 */
class ThumbnailCacheProbe {

    @Test
    fun `what the disk cache buys, and what a sweep would cost`() = withTempStorageRoot { root ->
        val stills = copies(root, "still", SampleMedia.file(BURST_HEIC), TILES)
        val clips = copies(root, "clip", SampleMedia.file(SampleMedia.episode(1, ".mkv")), TILES)
        val flat = copies(root, "flat", SampleMedia.file(CHAPTERS), TILES)
        val photos = copies(root, "photo", photoSizedStill(root, PHOTO_WIDTH, PHOTO_HEIGHT), TILES)

        // Classloading, ffmpeg init and JIT are paid here, outside every timing.
        runBlocking {
            loadStillCover(stills.first())
            generateVideoThumbnail(clips.first())
        }

        val rows = listOf(
            "HEIC stills" to measure(stills) { runBlocking { loadStillCover(it) } },
            "video, real frame" to measure(clips) { runBlocking { generateVideoThumbnail(it) } },
            "video, flat frame" to measure(flat) { runBlocking { generateVideoThumbnail(it) } },
            "stills, ${PHOTO_WIDTH}x$PHOTO_HEIGHT" to measure(photos) { runBlocking { loadStillCover(it) } }
        )

        println(
            renderThumbnailReport(
                tiles = TILES,
                rows = rows,
                syntheticEntries = SYNTHETIC_ENTRIES,
                sweepMs = timeSweep(File(root, "sweep"), SYNTHETIC_ENTRIES),
                encodings = encodedSizes(SampleMedia.file(STILL_PNG), PREVIEW_MAX_PX)
            )
        )
    }

    /**
     * Both passes over one population, plus the two diagnostics that say whether the
     * warm one meant anything: how many entries were rebuilt rather than read, and
     * whether the poster the cold pass produced is one `isMonochrome` rejects.
     */
    private fun measure(files: List<File>, load: (File) -> Unit): Pass {
        val cold = time(files, load) { posters().forEach { it.delete() } }
        val flat = posters().firstOrNull()?.let { readsFlat(it) } ?: true
        // Captured before the plant below replaces every entry, or this would report
        // the size of the fixture rather than the size of a poster.
        val built = posters()
        val bytes = built.sumOf { it.length() }

        // A poster of a size nothing here would produce, written at each entry's own
        // name. Comparing mtimes across the passes is what first caught the videos
        // never hitting their cache, and it stopped working the moment a hit began
        // calling `markUsed` on purpose — a rebuild and a read now move the file
        // identically. Its **length** does not.
        val planted = SampleMedia.file(STILL_PNG).readBytes()
        files.forEach { WokaStorage.thumbnailFile(it).writeBytes(planted) }
        val warm = time(files, load) {}
        val rebuilt = files.count {
            WokaStorage.thumbnailFile(it).length() != planted.size.toLong()
        }
        return Pass(cold, warm, rebuilt, flat, TILES, built.size, bytes)
    }

    /**
     * Unreadable counts as flat: the extractor treats both the same way.
     *
     * **Decoded through Skia, not `ImageIO`.** Entries are WebP since the v3 schema and
     * `ImageIO` has no reader for it, so an `ImageIO.read` here returned null for every
     * poster and this answered "flat" for all four populations — a diagnostic that
     * cannot fail is worth less than none.
     */
    private fun readsFlat(poster: File): Boolean {
        val decoded = runCatching {
            Image.makeFromEncoded(poster.readBytes()).toComposeImageBitmap().toAwtImage()
        }.getOrNull()
        return decoded?.let(::isMonochrome) ?: true
    }

    /** The in-memory cache is evicted before **both** passes, or the second measures a map. */
    private fun time(files: List<File>, load: (File) -> Unit, prepare: () -> Unit): Long {
        files.forEach { ImageBitmapCache.removeAllFor(it.absolutePath) }
        prepare()
        val started = System.nanoTime()
        files.forEach(load)
        return (System.nanoTime() - started) / 1_000_000
    }

    /** The posters only — the schema marker is not one, and deleting it forces a wipe. */
    private fun posters(): List<File> =
        WokaStorage.thumbnailsDir.listFiles { file: File -> file.extension == "webp" }.orEmpty().toList()
}
