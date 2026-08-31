package com.woka.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.woka.feature.editor.EditorSaveStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/**
 * How long a close request waits for a save that has not finished (ME-125).
 *
 * A recipe is a few hundred bytes and lands in milliseconds (§5); the only write
 * that can approach this is bucket B's re-encode, and bucket B is empty today
 * (§5.1). The cap is therefore never reached by a save that is making progress —
 * it exists for one that is not.
 */
internal const val EXIT_GRACE_MS = 5_000L

/**
 * The one gate both exit doors go through.
 *
 * **Why there is a gate at all.** `Done` closes the editor first and writes second
 * (§4.5), so the window is already back on the grid while the write is running —
 * exactly the moment a hand reaches for the close button. `EditorSaveStatus.scope`
 * survives the composition that started the save (ME-130); it does not survive the
 * process. Nothing is ever half-written — `writeAtomically` moves the temp file
 * last — so what a kill loses is the *edit*, silently, under a pill that said it was
 * saving.
 *
 * **The wait is deferred, never blocking.** Holding the EDT while `writeAtomically`
 * sleeps on IO would be a frozen window with an unpainted pill on it, which is worse
 * than the loss. [requested] instead leaves the composition running: the banner keeps
 * drawing "Saving photo.jpg…" and this effect calls [onExit] when the count reaches
 * zero.
 *
 * **It exits anyway after [EXIT_GRACE_MS].** A save gets no veto over quitting: the
 * user asked to close, the original photograph is intact whatever happens, and an
 * application that will not close is a worse defect than a lost recipe.
 *
 * **An open editor is asked first** (ME-160). Found by hand 2026-08-31: drawing a
 * rectangle and pressing the close button quit Woka with no question, because the count
 * above gates a save that is *already running* and in that sequence none had started.
 * Both verdicts fall through to the same count — `Save` because the editor starts its
 * write before it answers, `Leave` because there is then nothing to wait for.
 *
 * In `app/shell` rather than in `feature/editor/` because the thing being closed is
 * the application — `desktopApp` may name `app/`, and a launcher reaching into a
 * feature to ask whether it may quit is the dependency this avoids.
 *
 * **Two flows raced rather than `withTimeoutOrNull`.** Measured 2026-08-31: a Compose
 * test's `mainClock.advanceTimeBy` resumes a `delay` inside a `LaunchedEffect` and
 * does **not** expire a `withTimeoutOrNull` in the same effect — the timeout is
 * scheduled through `Delay.invokeOnTimeout`, which falls back to real time here. The
 * obvious spelling therefore left the cap testable only by waiting five real seconds,
 * which is no test at all. The shape below says the same thing with `delay`, which
 * the clock does drive.
 */
@Composable
fun ExitWhenQuiet(requested: Boolean, onExit: () -> Unit) {
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        // An editor answers for its own document (ME-160), and this wait carries no
        // cap: a question nobody has answered must not quit the application after
        // five seconds. `EditorPresence` is read once, here, because whether an
        // editor was up is settled at the moment the request landed.
        if (EditorPresence.isOpen) {
            snapshotFlow { AppExitRequest.verdict }.first { it != null }
        }
        // `snapshotFlow` emits its current value on collection, so a window closed
        // with nothing in flight leaves on this frame and never reaches the cap.
        merge(
            snapshotFlow { EditorSaveStatus.inFlight }.filter { it == 0 },
            flow {
                delay(EXIT_GRACE_MS)
                emit(0)
            }
        ).first()
        onExit()
    }
}
