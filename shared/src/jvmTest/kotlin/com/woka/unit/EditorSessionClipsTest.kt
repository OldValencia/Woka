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
import com.woka.feature.editor.core.detachFrame
import com.woka.feature.editor.core.layers
import com.woka.feature.editor.core.moveToFrame
import com.woka.feature.editor.core.ownOpsOf
import com.woka.feature.editor.core.preview
import com.woka.feature.editor.core.rebase
import com.woka.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A film's clips as session frames (ME-215): it opens on no clip, This Clip on clip 0 reads
 * clip 0's stored look back, and a split's new half is given its parent's look.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionClipsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun op(id: String) = ToolOp(id, ParamSet())

    private val shared = EditDocument(listOf(op("trim")))

    /** The file: a cut every clip draws, and a grade of clip 0's own. */
    private fun film() = EditorSession(
        media = MediaItem.Video(File("film.mp4")),
        initial = shared,
        openedOn = NO_CLIP,
        storedFor = { key -> if (key == 0) EditDocument(shared.ops + op("adjust")) else shared },
        storedBase = shared
    )

    @Test
    fun `this clip on clip 0 shows the look the file stored for it`() {
        val session = film()
        session.rebase()
        session.moveToFrame(0, EditScope.SingleFrame)
        assertEquals(shared.ops + op("adjust"), session.document.value.ops)
        assertEquals(listOf(op("adjust")), session.ownOpsOf(0))
    }

    @Test
    fun `a look made under this clip is that clip's, and all clips is the shared layer`() {
        val session = film()
        session.rebase()
        session.moveToFrame(1, EditScope.SingleFrame)
        session.preview(op("filter"))
        session.commit(ToolCardinality.Single)
        session.detachFrame()

        assertEquals(shared, session.document.value)
        val written = session.layers(EditScope.AllFrames)
        assertEquals(shared, written.base)
        assertEquals(listOf(op("filter")), written.frames.getValue(1).ops)
    }

    @Test
    fun `a copied layer is written under the new key`() {
        val session = film()
        session.copyFrame(from = 0, to = 4)
        assertEquals(listOf(op("adjust")), session.ownOpsOf(4))
        assertEquals(listOf(op("adjust")), session.layers(EditScope.AllFrames).frames.getValue(4).ops)
    }
}
