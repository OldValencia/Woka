package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.ClipLooks
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatValue
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.commit
import com.woka.feature.editor.core.ownOpsOf
import com.woka.feature.editor.core.panelDocument
import com.woka.feature.editor.core.pickClip
import com.woka.feature.editor.core.preview
import com.woka.feature.editor.core.sharedOps
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.useLinearHistory
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.support.FOLD_SOURCE
import com.woka.support.MainDispatcherRule
import com.woka.support.StageTool
import com.woka.support.toolOp
import com.woka.support.translation
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A clip's own op over the film's (review 1.0.7, F1 and F3): a picture tool's replaces the
 * film's everywhere the two are composed, and a sound tool's panel starts from the clip alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipLayeringTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun adjust(v: Float) = ToolOp(AdjustTool.manifest.id, ParamSet(mapOf("exposure" to FloatValue(v))))

    private fun sound(v: Float) = ToolOp(SoundTool.manifest.id, ParamSet(mapOf("volume" to FloatValue(v))))

    private fun film(shared: EditDocument) = EditorSession(
        media = MediaItem.Video(File("film.mp4")),
        initial = shared,
        openedOn = NO_CLIP,
        storedFor = { shared },
        storedBase = shared
    ).apply { useLinearHistory(setOf("trim", "crop")) }

    private fun EditorSession.edit(op: ToolOp) {
        preview(op)
        commit(ToolCardinality.Single)
    }

    /** The harness's tool, made `Single` — `StageTool` itself is `Repeating`. */
    private class SingleStage(inner: StageTool) : EditorTool by inner {
        override val manifest = inner.manifest.copy(cardinality = ToolCardinality.Single)
    }

    private val shiftStage = RasterStage.Geometry(translation(100f), IntSize(1, 1))

    private val twice = EditDocument(ops = listOf(toolOp("shift"), toolOp("shift")))

    @Test
    fun `a single tool's later op replaces its earlier one in the fold`() {
        withFoldRegistry(listOf(SingleStage(StageTool("shift", ToolDomain.Geometry, shiftStage)))) { registry, _ ->
            val recipe = fold(twice, registry, FOLD_SOURCE)
            assertEquals(Offset(100f, 0f), recipe.matrix.map(Offset.Zero), "applied once, not twice")
        }
    }

    @Test
    fun `a repeating tool's ops all still apply`() {
        withFoldRegistry(listOf(StageTool("shift", ToolDomain.Geometry, shiftStage))) { registry, _ ->
            val recipe = fold(twice, registry, FOLD_SOURCE)
            assertEquals(Offset(200f, 0f), recipe.matrix.map(Offset.Zero))
        }
    }

    @Test
    fun `other clips keep the film's look while a clip with its own is open`() {
        val session = film(EditDocument(listOf(adjust(0.5f))))
        session.pickClip("b", 1)
        session.edit(adjust(0.8f))
        val looks = ClipLooks(1, session.sharedOps) { session.ownOpsOf(it) }
        assertEquals(listOf(adjust(0.5f)), looks.documentFor(session.document.value, 2).ops)
    }

    @Test
    fun `a clip's sound panel starts from the clip, not from the film`() {
        val session = film(EditDocument(listOf(sound(50f))))
        session.pickClip("b", 1)
        assertEquals(emptyList(), session.panelDocument(session.document.value, SoundTool.manifest).ops)
        session.edit(sound(60f))
        assertEquals(listOf(sound(60f)), session.ownOpsOf(1))
        assertEquals(listOf(sound(50f)), session.sharedOps, "the film's sound stays under the clip")
    }

    @Test
    fun `a picture tool's panel still reads what the clip shows`() {
        val session = film(EditDocument(listOf(adjust(0.5f))))
        session.pickClip("b", 1)
        assertEquals(listOf(adjust(0.5f)), session.panelDocument(session.document.value, AdjustTool.manifest).ops)
    }
}
