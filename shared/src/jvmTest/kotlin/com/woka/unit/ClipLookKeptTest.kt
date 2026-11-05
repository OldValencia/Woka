package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.commit
import com.woka.feature.editor.core.copyFrame
import com.woka.feature.editor.core.layers
import com.woka.feature.editor.core.ownOpsOf
import com.woka.feature.editor.core.pickClip
import com.woka.feature.editor.core.preview
import com.woka.feature.editor.core.useLinearHistory
import com.woka.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 1.0.6 review: a clip with a saved look, selected and let go without a change, kept its key
 * in `touched` — and the writer drops every touched key missing from `frames`, so `Done` and
 * a render erased the look from the file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipLookKeptTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun op(id: String) = ToolOp(id, ParamSet())

    private val shared = EditDocument(listOf(op("trim")))
    private val clipOneStored = EditDocument(shared.ops + op("adjust"))

    private fun film() = EditorSession(
        media = MediaItem.Video(File("film.mp4")),
        initial = shared,
        openedOn = NO_CLIP,
        storedFor = { key -> if (key == 1) clipOneStored else shared },
        storedBase = shared
    ).apply { useLinearHistory(setOf("trim", "crop")) }

    @Test
    fun `a clip only looked at is not touched, so its saved look survives`() {
        val session = film()
        session.pickClip("b", 1)
        session.pickClip(null, NO_CLIP)
        session.preview(op("crop"))
        session.commit(ToolCardinality.Single)

        val layers = session.layers(EditScope.AllFrames)
        assertFalse(1 in layers.touched, "a visit alone told the writer to drop clip 1's look")
        assertFalse(1 in layers.frames)
    }

    /**
     * Key 1 here is a clip deleted earlier whose look is still in the file; a split then mints
     * key 1 again for a half cut from clip 0, which has no look of its own.
     */
    @Test
    fun `a new clip on a reused key does not inherit the old clip's look`() {
        val session = film()
        session.copyFrame(from = 0, to = 1)
        assertEquals(emptyList(), session.ownOpsOf(1), "the deleted clip's look came back on the new half")

        val layers = session.layers(EditScope.AllFrames)
        assertTrue(1 in layers.touched, "the stale stored look would stay in the file")
        assertFalse(1 in layers.frames)
    }

    @Test
    fun `a clip whose look was changed is touched and written`() {
        val session = film()
        session.pickClip("b", 1)
        session.preview(op("filter"))
        session.commit(ToolCardinality.Single)
        session.pickClip(null, NO_CLIP)

        val layers = session.layers(EditScope.AllFrames)
        assertTrue(1 in layers.touched)
        assertEquals(listOf(op("adjust"), op("filter")), layers.frames[1]?.ops)
    }
}
