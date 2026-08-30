package com.woka.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/**
 * What a reader has to know about an edit, and nothing more (ME-133).
 *
 * The fold lives in `feature/editor` and the readers live in `feature/viewer/image`
 * and `core`. A reader importing the editor would be one feature reaching into
 * another, which the layout forbids: `core/` is shared by everyone, a `feature/` is
 * shared by no one. So the seam is this interface — declared here, implemented over
 * there, and handed to [AppliedStillEdits] by `di/EditorModule.kt`.
 *
 * **In `commonMain`, not `jvmMain`**, because `StillCoverImage` is a `commonMain`
 * composable and has to ask [carries] before deciding which road a tile takes. Both
 * `ImageBitmap` and `File` are nameable here — this project has one JVM target, which
 * is why `core/utils/StillCover.kt` beside it already imports `java.io.File`.
 */
interface StillEdits {

    /**
     * Whether [file] carries an edit at all — a few kilobytes off its head, no decode.
     *
     * Asked once per visible grid tile, which is why it is a separate question from
     * [apply] rather than something inferred from it.
     *
     * **`suspend` because it touches the disk.** Its only caller is a `LaunchedEffect`,
     * which runs on the main dispatcher; making the implementation own the move to IO
     * is the difference between a rule and a rule somebody has to remember.
     */
    suspend fun carries(file: File): Boolean

    /**
     * [decoded] with the file's recipe folded over it, or **[decoded] itself** when
     * there is no recipe.
     *
     * Returning the same instance is load-bearing, not an optimisation: every still in
     * the app comes through here, and a picture that has never been edited must not pay
     * a surface allocation to find that out.
     */
    fun apply(file: File, frameIndex: Int, decoded: ImageBitmap): ImageBitmap

    /**
     * Which frames of [file] carry an edit **of their own** (ME-157).
     *
     * The third question of the same kind, so the seam grows a member rather than the
     * frame strip growing a dependency on the editor.
     *
     * It answers frames with a key in the payload, not frames that draw an edit: a
     * file-wide recipe applies to every frame, and marking all five would say nothing
     * that "photo.heic · Edited" does not already say better. A per-frame override is
     * the thing that can be invisible, so it is the thing that gets a mark.
     *
     * `suspend` for the reason [carries] is, and defaulted to nothing for the reason
     * [carries] is not: a medium with no notion of a keyed recipe has an honest empty
     * answer, while "does this carry an edit" has no default that is not a lie.
     */
    suspend fun editedFrames(file: File): Set<Int> = emptySet()
}

/** No editor in the graph: every file is unedited and every bitmap passes through. */
private object NoStillEdits : StillEdits {
    override suspend fun carries(file: File): Boolean = false
    override fun apply(file: File, frameIndex: Int, decoded: ImageBitmap): ImageBitmap = decoded
}

/**
 * The one the readers actually call.
 *
 * A process-wide holder rather than an injected dependency because the readers are an
 * `object` (`ImageLoader`, `StillPreview`), a `commonMain` composable and a top-level
 * function — none of them has a constructor to inject into, and giving three of them a
 * Koin lookup each would be more wiring than the seam is worth.
 *
 * **The default is the identity**, so `core` and the viewer build and behave exactly as
 * they did before the editor existed. That is also what makes them testable without one.
 */
object AppliedStillEdits : StillEdits {

    @Volatile
    private var installed: StillEdits = NoStillEdits

    /** Called once, at startup, by the module that owns the implementation. */
    fun install(edits: StillEdits) {
        installed = edits
    }

    override suspend fun carries(file: File): Boolean = installed.carries(file)

    override fun apply(file: File, frameIndex: Int, decoded: ImageBitmap): ImageBitmap =
        installed.apply(file, frameIndex, decoded)

    override suspend fun editedFrames(file: File): Set<Int> = installed.editedFrames(file)
}
