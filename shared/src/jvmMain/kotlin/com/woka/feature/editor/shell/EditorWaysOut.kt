package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.woka.core.data.HdrView
import com.woka.core.data.HdrViewMode
import com.woka.core.data.MediaKind
import com.woka.core.data.StillColorInfo
import com.woka.core.data.needsToneCurve
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.commit
import com.woka.feature.editor.core.drop
import com.woka.feature.editor.core.preview
import com.woka.feature.editor.tools.resize.ResizeTool
import com.woka.feature.editor.tools.resize.SizeChoice
import com.woka.feature.editor.tools.resize.SizeLimits
import com.woka.feature.editor.tools.resize.photoSizePresets
import com.woka.feature.editor.tools.resize.resizeOpFor
import com.woka.feature.editor.tools.resize.savedSize
import com.woka.feature.editor.tools.resize.videoSizePresets

/**
 * What the dialogs of RD-401 read off the editor and write back into it: the size group's
 * starting state, the colour mode's note, and a chosen size as a step of history.
 */

/**
 * The size group for `Save your changes?` ([copy] false) or `Create a copy` ([copy] true),
 * made once per opening. Its frame is the picture after crop and turns, **before** Resize;
 * `Done` opens at what the document saves, a copy always at `Original`.
 */
@Composable
internal fun rememberOutputSize(engine: EditorEngine, session: EditorSession, copy: Boolean): OutputSizeState =
    remember(engine, session, copy) {
        val document = session.document.value
        val frame = engine.drawnSize(document.without(ResizeTool.manifest.id))
        val video = engine.kind == MediaKind.Video
        OutputSizeState(
            frame = frame,
            presets = if (video) videoSizePresets(frame) else photoSizePresets(frame),
            limits = if (video) SizeLimits.Video else SizeLimits.Photo,
            saved = if (copy) SizeChoice.Original else document.savedSize()
        )
    }

/**
 * The colour mode's name when it changes [color]'s picture — `SDR` on an HDR file, `HDR` on
 * an SDR one — or null. A copy follows the screen (the author, 2026-11-27), so `Create a copy`
 * says when that means a different tone mapping than the file's own.
 */
internal fun retoneOf(color: StillColorInfo, mode: HdrViewMode = HdrView.mode): String? = when {
    mode == HdrViewMode.ForceSdr && needsToneCurve(color.transfer) -> "SDR"
    mode == HdrViewMode.ForceHdr && !needsToneCurve(color.transfer) -> "HDR"
    else -> null
}

/**
 * `Save`'s size written into the session as one step, before the save reads it: `Original`
 * takes the Resize out, anything else replaces it. Nothing is written when the choice is
 * what the document already saves, so an untouched dialog adds no history.
 */
internal fun EditorSession.saveSize(choice: SizeChoice) {
    if (choice == document.value.savedSize()) return
    val op = resizeOpFor(choice) ?: return drop(ResizeTool.manifest.id)
    preview(op)
    commit(ToolCardinality.Single)
}
