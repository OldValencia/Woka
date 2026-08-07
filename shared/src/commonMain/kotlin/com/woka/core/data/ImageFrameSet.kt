package com.woka.core.data

import androidx.compose.runtime.Immutable

/**
 * What a frame inside an image file is for.
 *
 * A HEIC is a container, not a picture: a burst is a run of [Sequence] images, a
 * Live Photo pairs a [Primary] still with a [Motion] clip, and a portrait shot
 * carries a [Depth] map beside the photograph. macOS Preview puts these in a
 * strip and lets you walk them; so should the viewer.
 *
 * [Depth] and [Auxiliary] are **recorded but not offered**. They are not
 * alternative views of the picture — a depth map shown as a frame is a grey
 * smear that looks like a decode failure — but throwing them away would mean
 * re-deriving them the day the editor wants them.
 */
enum class FrameKind { Primary, Sequence, Auxiliary, Depth, Motion }

/**
 * One frame inside one file.
 *
 * [index] is the decoder's own index, whatever that decoder counts in: skia
 * counts animation frames, `ImageIO` counts TIFF pages, and FFmpeg counts
 * **video streams** — a five-shot HEIC burst is five one-frame streams, not one
 * stream of five frames (measured; see the plan's Phase 0). The number is
 * meaningful only to the decoder that produced the set.
 *
 * [durationMs] is non-null only for animation. A burst has no timing: its frames
 * were taken milliseconds apart but they are not a sequence to be *played*.
 */
@Immutable
data class ImageFrame(
    val index: Int,
    val kind: FrameKind,
    val durationMs: Int? = null
)

/**
 * Everything inside one image file.
 *
 * The unifying idea of the frame-set work: a still is not "a bitmap", it is a
 * frame set, of which the overwhelmingly common case has exactly one frame. That
 * case is [single], and it must stay free — a normal photo has to produce the
 * same tree it always did, with no strip and no extra decode.
 */
@Immutable
data class ImageFrameSet(
    val frames: List<ImageFrame>,
    val primaryIndex: Int = 0
) {
    val isSingle: Boolean get() = frames.size <= 1

    /**
     * The frames the viewer offers, in file order — [FrameKind.Depth] and
     * [FrameKind.Auxiliary] are filtered out here and nowhere else, so a caller
     * cannot forget.
     */
    val viewable: List<ImageFrame>
        get() = frames.filter { it.kind in VIEWABLE_KINDS }

    /**
     * The frame to open the file on.
     *
     * [primaryIndex] is out of range whenever a decoder disagrees with itself
     * about how many images it found, so it falls back to the first frame rather
     * than throwing — a viewer that shows the wrong frame is a nuisance, one
     * that crashes on a malformed HEIC is a bug report.
     */
    val primary: ImageFrame?
        get() = frames.getOrNull(primaryIndex) ?: frames.firstOrNull()

    companion object {
        private val VIEWABLE_KINDS =
            setOf(FrameKind.Primary, FrameKind.Sequence, FrameKind.Motion)

        /** What every ordinary photograph is, and what a decoder falls back to. */
        val Single = ImageFrameSet(listOf(ImageFrame(0, FrameKind.Primary)))
    }
}
