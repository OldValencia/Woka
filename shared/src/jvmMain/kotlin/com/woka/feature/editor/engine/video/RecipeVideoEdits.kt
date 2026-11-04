package com.woka.feature.editor.engine.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.core.data.StillColorInfo
import com.woka.core.data.mediaKind
import com.woka.core.ui.image.AppliedVideoEdit
import com.woka.core.ui.image.EditedClock
import com.woka.core.ui.image.SoundMix
import com.woka.core.ui.image.SourceRange
import com.woka.core.ui.image.VideoEdits
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.RecipePainter
import com.woka.feature.editor.engine.image.scaledBy
import com.woka.feature.editor.engine.video.render.isIdentity
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.save.renderAtNativeScale
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * `VideoEdits`, answered from the recipe a film carries (ME-284) — the video half of
 * `RecipeStillEdits`, installed beside it by `EditorModule`.
 *
 * **Cached on path, length and mtime**, the key `SaveEviction` already trusts for a
 * poster: the viewer asks on the frame path, so an unedited film must cost a stat, and a
 * `Done` — an append — changes both length and mtime, so the next ask sees the new edit.
 *
 * The fold is the editor's own (`fold`, `isIdentity`, `drawThroughRecipe` behind the
 * painter, `renderAtNativeScale` for the poster), addressed in the same coded frame size
 * the editor folds against, so a markup lands where it was placed. **Never throws**: a
 * payload that does not read is a film drawn as shot.
 */
internal class RecipeVideoEdits(private val registry: EditorToolRegistry) : VideoEdits {

    private val folded = ConcurrentHashMap<String, Pair<Long, AppliedVideoEdit?>>()

    override fun editOf(file: File): AppliedVideoEdit? {
        if (file.mediaKind() != MediaKind.Video) return null
        val stamp = file.length() * 31 + file.lastModified()
        folded[file.absolutePath]?.let { (seen, edit) -> if (seen == stamp) return edit }
        val edit = runCatching { foldFile(file) }.getOrNull()
        folded[file.absolutePath] = stamp to edit
        return edit
    }

    private fun foldFile(file: File): AppliedVideoEdit? {
        val payload = EditRecipe.read(file)?.takeIf { !it.isEmpty } ?: return null
        val sourceSize = codedFrameSize(file)
        if (sourceSize == IntSize.Zero) return null
        val base = EditDocument(payload.ops)
        val ranges = TrimTool.clipsIn(base).orEmpty().map { SourceRange(it.fromMs, it.toMs, it.key, it.speed, it.holdMs, SoundTool.mixIn(payload.frames[it.key]?.ops.orEmpty())) }
        // One recipe per layer the cut draws (ME-215); an uncut film is clip 0 alone.
        val keys = ranges.map { it.key }.ifEmpty { listOf(0) }.distinct()
        val recipes = keys.associateWith { key ->
            previewFold(payload.documentFor(key), registry, sourceSize).takeUnless { it.isIdentity(sourceSize) }
        }
        return FoldedVideoEdit(EditedClock(ranges), sourceSize, recipes, SoundTool.mixIn(payload.ops))
    }
}

private class FoldedVideoEdit(
    override val clock: EditedClock,
    private val sourceSize: IntSize,
    /** By clip layer key; a null value is a clip drawn as decoded — cuts alone (ME-215). */
    private val recipes: Map<Int, RasterRecipe?>,
    override val wholeSound: SoundMix?
) : AppliedVideoEdit {

    /** The playing clip's recipe, read off [clock] so a join recomposes the reader. */
    private val recipe: RasterRecipe?
        get() = recipes[clock.ranges.getOrNull(clock.index)?.key ?: 0]

    override fun painter(frame: ImageBitmap, color: StillColorInfo, moving: Boolean): Painter? {
        val recipe = recipe ?: return null
        // A moving frame arrives untone-mapped (ME-205), so its HDR pass leads the chain.
        val hdr = if (moving) hdrStageFor(HdrInfo(color.transfer, color.isWideGamut)) else null
        return RecipePainter(frame, sourceSize, recipe.copy(source = hdr))
    }

    /** The first clip's look — the cover, until ME-287 lets the author choose one. */
    override fun poster(poster: ImageBitmap): ImageBitmap {
        val recipe = recipes[clock.ranges.firstOrNull()?.key ?: 0] ?: return poster
        val factor = poster.width.toFloat() / sourceSize.width
        return runCatching {
            renderAtNativeScale(poster, IntSize(poster.width, poster.height), recipe.scaledBy(factor))
                .toComposeImageBitmap()
        }.getOrDefault(poster)
    }
}
