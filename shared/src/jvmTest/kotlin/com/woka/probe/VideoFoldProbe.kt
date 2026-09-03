package com.woka.probe

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.image.drawRecipeImage
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.adjust.adjustStage
import org.jetbrains.skia.Surface
import kotlin.test.Test

/** A second of video at 60 fps, and never more: one case here can cost two seconds a draw. */
private const val MAX_DRAWS = 60

/** …so the slow cases stop on time instead, and report how many draws they managed. */
private const val BUDGET_MS = 1_500

private const val WARMUP = 2

/**
 * What folding a document costs **per drawn frame**, stage by stage (ME-201).
 *
 * The image canvas folds once per bitmap. The video canvas folds per presented frame,
 * 24–60 times a second, and nothing here had ever been asked for that.
 *
 *     ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.VideoFoldProbe" --rerun -i
 *
 * **What this is not.** It draws into a **CPU raster** surface, because that is what a
 * headless test can make; the app draws through skiko's hardware backend, where a shader
 * chain is a fragment shader rather than per-pixel SkVM. The absolute milliseconds are a
 * ceiling and **not the frame budget** — the 16 ms question is answered by hand against
 * the real canvas and recorded in ME-201 with a date.
 *
 * **What it is for** is which *stage* costs what. One slider per row rather than one
 * document, because "the chain is expensive" is not something anyone can act on and
 * "the convolution is expensive" is.
 */
class VideoFoldProbe {

    private val sizes = listOf("1080p" to IntSize(1920, 1080), "2160p" to IntSize(3840, 2160))

    /** One slider each, so a row names the stage it added rather than a document. */
    private val documents = listOf(
        "empty" to ParamSet(),
        "matrix (Exposure)" to ParamSet().with(AdjustTool.Exposure, 20f),
        "tone curve" to ParamSet().with(AdjustTool.Highlights, 30f),
        "noise" to ParamSet().with(AdjustTool.Noise, 25f),
        "sharpen" to ParamSet().with(AdjustTool.Sharpness, 40f),
        "all four" to ParamSet()
            .with(AdjustTool.Exposure, 20f)
            .with(AdjustTool.Highlights, 30f)
            .with(AdjustTool.Noise, 25f)
            .with(AdjustTool.Sharpness, 40f)
    )

    @Test
    fun `what a fold costs per drawn frame`() {
        val rows = sizes.flatMap { (label, size) -> rowsFor(label, size) }

        println("Video fold probe — CPU raster, mean per draw, $BUDGET_MS ms or $MAX_DRAWS draws")
        println(renderTable(listOf("size", "document", "draws", "ms / draw", "× plain"), rows))
        println()
        println("`× plain` carries: each stage against one drawImage. CPU raster is a ceiling.")
    }

    private fun rowsFor(label: String, size: IntSize): List<List<String>> {
        val source = syntheticFrame(size)
        val surface = Surface.makeRasterN32Premul(size.width, size.height)
        val cases = documents.map { (name, params) -> name to recipeFor(size, params) }
        val withPaint = cases.last().let { (name, recipe) ->
            "$name + a paint" to recipe.copy(
                paints = listOf(RasterStage.Paint { drawRect(Color.Red, size = Size(64f, 64f)) })
            )
        }
        val baseline = measure(surface, source, size, cases.first().second).first
        return (cases + withPaint).map { (name, recipe) ->
            val (ms, draws) = measure(surface, source, size, recipe)
            listOf(label, name, "$draws", "%.1f".format(ms), "%.1f×".format(ms / baseline))
        }
    }

    /** The document folded the way the tool folds it — a matrix, shaders, or both. */
    private fun recipeFor(size: IntSize, params: ParamSet): RasterRecipe {
        val plain = RasterRecipe(
            matrix = Matrix(),
            outputSize = size,
            colorFilter = null,
            paints = emptyList(),
            skipped = emptyList()
        )
        val stages = adjustStage(params)?.let { flatten(it as RasterStage) } ?: return plain
        val matrix = stages.filterIsInstance<RasterStage.Pixels>().singleOrNull()
        return plain.copy(
            colorFilter = matrix?.let { ColorFilter.colorMatrix(it.colorMatrix) },
            shaders = stages.filterIsInstance<RasterStage.Shader>()
        )
    }

    private fun flatten(stage: RasterStage): List<RasterStage> = when (stage) {
        is RasterStage.Composite -> stage.stages.flatMap { flatten(it) }
        else -> listOf(stage)
    }

    /** Mean milliseconds per draw and how many draws that mean is over. */
    private fun measure(
        surface: Surface,
        source: ImageBitmap,
        size: IntSize,
        recipe: RasterRecipe
    ): Pair<Double, Int> {
        repeat(WARMUP) { draw(surface, source, size, recipe) }
        val start = System.nanoTime()
        var draws = 0
        while (draws < MAX_DRAWS && (System.nanoTime() - start) / 1_000_000 < BUDGET_MS) {
            draw(surface, source, size, recipe)
            draws++
        }
        return (System.nanoTime() - start) / 1_000_000.0 / draws to draws
    }

    private fun draw(surface: Surface, source: ImageBitmap, size: IntSize, recipe: RasterRecipe) {
        val box = Size(size.width.toFloat(), size.height.toFloat())
        val canvas = surface.canvas.asComposeCanvas()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, box) {
            drawRecipeImage(source, size, recipe)
        }
    }

    /** Two blocks, not a flat fill: a uniform frame is one a sharpener sees nothing in. */
    private fun syntheticFrame(size: IntSize): ImageBitmap {
        val bitmap = ImageBitmap(size.width, size.height)
        val box = Size(size.width.toFloat(), size.height.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), box) {
            drawRect(Color(0xFF8E5B2F))
            drawRect(Color(0xFF26537A), size = Size(box.width / 2f, box.height))
        }
        return bitmap
    }
}
