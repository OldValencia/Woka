package com.woka.probe

import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.ANIM_WEBP
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.HDR_HLG_AVIF
import com.woka.fixtures.HDR_PQ_AVIF
import com.woka.fixtures.HDR_PQ_PNG
import com.woka.fixtures.LIVE_PHOTO_MOTION
import com.woka.fixtures.LIVE_PHOTO_STILL
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.STILL_AVIF
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.STILL_WEBP
import com.woka.fixtures.SampleMedia
import kotlin.test.Test

/**
 * Phase 0 of `ai/docs/features/pending/rare-image-formats-and-frame-sets.md`.
 *
 * **It reports, it does not assert.** The bundled decoder set is a fact to be
 * measured, not assumed — the tone-mapping work already paid for that lesson
 * once, when `avfilter-9.dll` turned out to ship `tonemap` but neither `zscale`
 * nor `libplacebo`, closing off a filter-graph design after it was written.
 *
 * So this always passes. Its output is the deliverable: run it, paste the two
 * tables into the plan, and write the decoder routing from them. Nothing here
 * belongs in a red/green suite, because "the bundled build cannot do X" is not
 * a regression — it is the input to a decision.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.*" -i
 */
class DecoderCapabilityProbe {

    /**
     * The frame-set corpus, plus the three stills that already work — a probe
     * with no known-good row cannot tell "this format fails" from "this probe
     * fails".
     */
    private val fixtures = listOf(
        BURST_HEIC,
        LIVE_PHOTO_STILL,
        LIVE_PHOTO_MOTION,
        STILL_AVIF,
        HDR_PQ_AVIF,
        HDR_HLG_AVIF,
        HDR_PQ_PNG,
        ANIM_GIF,
        ANIM_WEBP,
        MULTIPAGE_TIFF,
        STILL_PNG,
        STILL_JPG,
        STILL_WEBP
    )

    /**
     * `hevc` and `av1` decide whether HEIC and AVIF are decodable at all; the
     * demuxers decide whether their containers can be opened to reach them.
     * `image2` is the fallback path for a single-image file with no container
     * of its own.
     */
    private val decoders = listOf("hevc", "av1", "libdav1d", "libaom-av1", "webp", "gif", "tiff")

    private val demuxers = listOf("avif", "heif", "image2", "mov,mp4,m4a,3gp,3g2,mj2", "gif")

    @Test
    fun `what the bundled decoders make of each fixture`() {
        val rows = fixtures.map { name ->
            val file = SampleMedia.file(name)
            val skia = probeSkia(file)
            val ffmpeg = probeFfmpeg(file)
            listOf(
                "`$name`",
                skiaCodecCell(skia),
                skiaImageCell(skia),
                ffmpegCell(ffmpeg),
                imageIoCell(probeImageIo(file)),
                colourCell(skia, ffmpeg)
            )
        }
        println(
            renderTable(
                headers = listOf(
                    "File", "skia `Codec`", "skia `Image`", "bundled FFmpeg",
                    "JDK `ImageIO`", "colour metadata"
                ),
                rows = rows
            )
        )
    }

    @Test
    fun `which decoders and demuxers the bundled build carries`() {
        val rows = decoders.map { listOf("decoder", "`$it`", presenceCell(decoderPresent(it))) } +
            demuxers.map { listOf("demuxer", "`$it`", presenceCell(demuxerPresent(it))) }
        println(renderTable(listOf("Kind", "Name", "Present"), rows))
    }
}
