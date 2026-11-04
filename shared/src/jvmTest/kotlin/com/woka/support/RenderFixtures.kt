package com.woka.support

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.video.render.renderVideo
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.probe.generateClip
import java.io.File
import java.nio.file.Files
import kotlin.test.assertIs

/**
 * What the render tests share: a generated 4 s clip with a keyframe every half second, a
 * registry holding Trim and a red tint, and a render that must succeed. Split out of
 * `VideoRenderTest` when the time tests (ME-216, ME-217) took it past the size mandate.
 */
internal val RENDER_SIZE = IntSize(640, 360)

private val tint = StageTool("tint", ToolDomain.Color, RasterStage.Pixels(scaleRed(2f)))

internal fun onClip(block: (File, EditorToolRegistry) -> Unit) = withTempStorageRoot {
    val dir = Files.createTempDirectory("woka-render").toFile()
    try {
        val source = File(dir, "clip.mp4")
        generateClip(source, "libopenh264", seconds = 4, gop = 15)
        block(source, EditorToolRegistry(listOf(TrimTool, tint), SettingsRepositoryImpl()))
    } finally {
        dir.deleteRecursively()
    }
}

internal fun cuts(vararg ranges: Pair<Long, Long>) = EditDocument(
    ops = listOf(TrimTool.opFor(ranges.mapIndexed { i, (from, to) -> TrimClip("c$i", from, to) }))
)

internal fun render(source: File, document: EditDocument, registry: EditorToolRegistry): File {
    val result = renderVideo(source, document, { emptyList() }, registry, RENDER_SIZE, alive = { true }, progress = {})
    return File(assertIs<EditSaveResult.Saved>(result, "the render failed: $result").path)
}
