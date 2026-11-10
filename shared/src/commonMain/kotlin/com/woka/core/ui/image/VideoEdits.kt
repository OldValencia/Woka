package com.woka.core.ui.image

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import com.woka.core.data.StillColorInfo
import java.io.File

/**
 * What the viewer, PiP and the grid have to know about a film's edit — `StillEdits`' seam,
 * for a moving picture (ME-284).
 *
 * **Every reader folds, or `Done` is a half-truth** (`save.md` §3.5). The author edited an
 * `mp4`, pressed `Done`, and found the markup in the editor and nowhere else — the rule
 * `tickets.md` §4 was written over. The fold lives in `feature/editor` and the readers in
 * `feature/viewer` and `core`; one feature may not reach into another, so the seam is
 * declared here and installed by `di/EditorModule.kt`, exactly as [AppliedStillEdits] is.
 */
interface VideoEdits {

    /**
     * The edit [file] carries, or null for a film that carries none — the common case,
     * which must cost a stat and a cache lookup and nothing more, because the viewer asks
     * it on the frame path.
     */
    fun editOf(file: File): AppliedVideoEdit?
}

/** One film's edit, folded once, asked per frame. */
interface AppliedVideoEdit {

    /**
     * What plays, in order, and where the player is in it ([EditedClock]). Its ranges are
     * empty when nothing is cut: the whole file plays as it always did.
     */
    val clock: EditedClock

    /**
     * [frame] drawn through the edit, at the edit's own size — a crop's proportions, not
     * the frame's — or null when the edit changes nothing on the picture (a cut alone),
     * so the viewer keeps its own painter. The clip is the one [clock] is in (ME-215), so a
     * look that is one clip's own draws there and nowhere else. [color] and [moving] are the viewer's own
     * answers, for the HDR pass that has to run before the edit grades anything. [sourceMs]
     * is where in the file [frame] stands, so timed markup fades as it did in the editor
     * (ME-263); null draws every shape whole.
     */
    fun painter(frame: ImageBitmap, color: StillColorInfo, moving: Boolean, sourceMs: Long? = null): Painter?

    /**
     * The grid's poster, edited — through the look of the clip [coverMs] falls in, else the
     * first clip's; [poster] itself when the picture is untouched.
     */
    fun poster(poster: ImageBitmap): ImageBitmap

    /**
     * The **source** millisecond of the cover the author chose (ME-287), for the poster to
     * be taken there; null leaves the choice to the grid's own guess.
     */
    val coverMs: Long? get() = null

    /** What the edit does to the whole film's sound (ME-240), or null for nothing. */
    val wholeSound: SoundMix? get() = null
}

/**
 * A stretch of the source, `[fromMs, toMs)`, drawing clip layer [key]'s look (ME-215) and
 * playing at [speed] (ME-216).
 */
@Immutable
data class SourceRange(
    val fromMs: Long,
    val toMs: Long,
    val key: Int = 0,
    val speed: Float = 1f,
    /** A freeze's hold (ME-217): the frame at [fromMs], shown this long. */
    val holdMs: Long = 0L,
    /** This clip's own sound (ME-240), or null for none. */
    val sound: SoundMix? = null
) {
    val isFreeze: Boolean get() = holdMs > 0L

    /** How long it lasts on the result. */
    val lengthMs: Long get() = if (isFreeze) holdMs else ((toMs - fromMs) / speed.toDouble()).toLong()
}

/** No editor in the graph: no film carries an edit. */
private object NoVideoEdits : VideoEdits {
    override fun editOf(file: File): AppliedVideoEdit? = null
}

/** The one the readers call; the identity until the editor installs itself. */
object AppliedVideoEdits : VideoEdits {

    @Volatile
    private var installed: VideoEdits = NoVideoEdits

    /**
     * Moves on every successful video write, and the readers `remember` on it (reported
     * 2026-11-02: a trim showed only after leaving the viewer and coming back). Keyed on
     * the file's mtime they re-read only when they happened to recompose — and a paused
     * film, and the slider beside it, do not recompose after `Done`.
     */
    var generation by mutableIntStateOf(0)
        private set

    /** Called by the editor after it wrote into a film. */
    fun changed() {
        Snapshot.withMutableSnapshot { generation++ }
    }

    fun install(edits: VideoEdits) {
        installed = edits
    }

    override fun editOf(file: File): AppliedVideoEdit? = installed.editOf(file)
}
