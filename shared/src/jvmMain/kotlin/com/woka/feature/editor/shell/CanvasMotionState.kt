package com.woka.feature.editor.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import com.woka.feature.editor.core.CanvasMotion
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ToolOp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A turn or a flip takes this long on the canvas (RD-203). */
private const val MOTION_MS = 320

/**
 * The picture turning or mirroring into its new state instead of jumping there (RD-203).
 *
 * The engine draws the result at once; this lays the inverse movement over it and runs it back
 * to nothing — a turn from where the picture was, a flip through its edge. A quarter turn also
 * scales from the old picture's size, since the turned one fits the canvas differently.
 */
internal class CanvasMotionState {

    private val progress = Animatable(1f)
    private var motion by mutableStateOf<CanvasMotion?>(null)

    /** The picture's size while nothing moves — what a quarter turn starts from. */
    private var settled = Size.Zero

    val modifier: Modifier = Modifier.graphicsLayer {
        val playing = motion
        val left = 1f - progress.value
        if (playing == null || left <= 0f) {
            settled = size
            return@graphicsLayer
        }
        rotationZ = -playing.degrees * left
        val quarter = abs(playing.degrees) % 180f == 90f && size.height > 0f && settled != Size.Zero
        val scale = 1f + ((if (quarter) settled.width / size.height else 1f) - 1f) * left
        scaleX = scale * (if (playing.flipX) 1f - 2f * left else 1f)
        scaleY = scale * (if (playing.flipY) 1f - 2f * left else 1f)
    }

    suspend fun play(next: CanvasMotion) {
        motion = next
        progress.snapTo(0f)
        progress.animateTo(1f, tween(MOTION_MS, easing = EditorTokens.Motion.easing))
    }
}

/**
 * Watches the document for one operation appended — a press, not an undo or a load — and
 * plays the movement its tool reports for it. Started in the same frame the new picture is
 * composed, so its first draw is already turned back.
 */
@Composable
internal fun rememberCanvasMotion(session: EditorSession, tools: List<EditorTool>): CanvasMotionState {
    val state = remember(session) { CanvasMotionState() }
    val scope = rememberCoroutineScope()
    val document by session.document.collectAsState()
    var seen by remember(session) { mutableStateOf(document.ops) }
    val ops = document.ops
    SideEffect {
        val before = seen
        if (ops === before) return@SideEffect
        seen = ops
        val added = appendedOp(before, ops) ?: return@SideEffect
        val next = tools.firstOrNull { it.manifest.id == added.toolId }?.canvasMotion(added) ?: return@SideEffect
        scope.launch(start = CoroutineStart.UNDISPATCHED) { state.play(next) }
    }
    return state
}

/** The one operation [after] adds to [before], or null for any other change. */
private fun appendedOp(before: List<ToolOp>, after: List<ToolOp>): ToolOp? =
    after.lastOrNull()?.takeIf { after.size == before.size + 1 && after.subList(0, before.size) == before }
