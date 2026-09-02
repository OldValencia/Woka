package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.commit
import com.woka.feature.editor.core.detachFrame
import com.woka.feature.editor.core.layers
import com.woka.feature.editor.core.moveToFrame
import com.woka.feature.editor.core.ownOpsOfOpenFrame
import com.woka.feature.editor.core.preview
import com.woka.feature.editor.core.rebase
import com.woka.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rules the scope switch and the frame walk must never break, checked over a long
 * sequence rather than a scenario (2026-09-02).
 *
 * **Why this file exists.** Every defect in this pair of modes was found by hand, one
 * symptom at a time, and each fix was aimed at the symptom. Three of the four rules
 * below had been broken at least once by the time they were written down. A test per
 * reported bug catches that bug; a test per rule catches the next one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionInvariantsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun op(id: String) = ToolOp(id, ParamSet())

    private fun session() = EditorSession(
        media = MediaItem.Image(File("burst.heic")),
        initial = EditDocument(),
        openedOn = 0,
        storedFor = { EditDocument() }
    )

    private fun EditorSession.edit(id: String) {
        preview(op(id))
        commit(ToolCardinality.Single)
    }

    /**
     * Every switch and every walk a sitting can make, in an order that has hit all four
     * rules at some point. Each step asserts the whole set, so a break is reported at the
     * step that caused it rather than at the end.
     */
    private fun EditorSession.walkEverything(check: (String) -> Unit) {
        edit("a"); check("edited in All Frames")
        moveToFrame(2, EditScope.AllFrames); check("walked in All Frames")
        rebase(); check("switched to Single Frame")
        edit("b"); check("edited the frame's own")
        detachFrame(); check("switched back to All Frames")
        rebase(); check("switched to Single Frame again")
        moveToFrame(0, EditScope.SingleFrame); check("walked in Single Frame")
        edit("c"); check("edited another frame's own")
        moveToFrame(2, EditScope.SingleFrame); check("walked back")
    }

    /**
     * **The stash holds the open frame only while the layer is what is shown.**
     *
     * That is what `All Frames` means — the frame's own work is put away so the layer can
     * be seen — and it is the one state in which the two coexist. Anywhere else it is a
     * frame whose work nothing will ever show again.
     */
    @Test
    fun `the open frame is stashed only while the layer is shown`() {
        val session = session()
        session.walkEverything { step ->
            val stashed = session.frame in session.elsewhere.value
            val showingLayer = session.ownOpsOfOpenFrame().isEmpty()
            assertTrue(
                !stashed || showingLayer,
                "after $step the open frame was stashed while its own work was on screen"
            )
        }
    }

    /** **Nothing the sitting made is ever lost**, wherever it currently lives. */
    @Test
    fun `every edit made is still somewhere`() {
        val session = session()
        val made = mutableSetOf<ToolOp>()
        val expected = listOf("a", "b", "c")
        var next = 0
        session.walkEverything { step ->
            while (next < expected.size && op(expected[next]) in session.document.value.ops) {
                made += op(expected[next]); next++
            }
            val held = session.document.value.ops.toSet() +
                session.elsewhere.value.values.flatMap { it.document.ops }
            made.forEach { assertTrue(it in held, "after $step, ${it.toolId} was nowhere") }
        }
    }

    /** **And `Done` writes all of it**, in whichever layer it belongs to. */
    @Test
    fun `nothing made is missing from what done writes`() {
        val session = session()
        session.walkEverything { }

        val written = session.layers(EditScope.SingleFrame)
        val all = written.base.ops + written.frames.values.flatMap { it.ops }
        listOf("a", "b", "c").forEach { id ->
            assertTrue(op(id) in all, "$id was never written")
        }
    }

    /** **The shared layer only ever grows by shared work**, never by a frame's own. */
    @Test
    fun `a frame's own work never reaches the shared layer`() {
        val session = session()
        session.walkEverything { }

        assertEquals(listOf(op("a")), session.layers(EditScope.SingleFrame).base.ops)
    }
}
