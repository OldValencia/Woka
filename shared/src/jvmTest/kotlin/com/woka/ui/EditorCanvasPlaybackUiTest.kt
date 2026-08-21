package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.shell.CANVAS_HOST_TAG
import com.woka.feature.editor.shell.EditorCanvasHost
import com.woka.support.FixtureEngine
import com.woka.support.onUi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The canvas click, which is how an animation on the editing surface is paused and
 * resumed (the same gesture the viewer uses one panel below).
 *
 * Asked of the **shell**, against `FixtureEngine`: what is being tested is that the
 * canvas asks its engine, not what any particular engine does about it. A photograph's
 * engine answers the same call by doing nothing, which is why the gesture is
 * unconditional and needs no knowledge of the medium here (§3.3).
 */
@OptIn(ExperimentalTestApi::class)
class EditorCanvasPlaybackUiTest {

    @Test
    fun `a click on the canvas asks the engine to start or stop`() {
        val engine = FixtureEngine()
        onUi {
            setContent { EditorCanvasHost(engine = engine, document = EditDocument()) }
            waitForIdle()
            assertEquals(0, engine.playbackToggles, "composing the canvas toggled something")

            onNodeWithTag(CANVAS_HOST_TAG).performClick()
            waitForIdle()
            assertEquals(1, engine.playbackToggles)

            onNodeWithTag(CANVAS_HOST_TAG).performClick()
            waitForIdle()
            assertEquals(2, engine.playbackToggles, "two clicks must be two toggles")
        }
    }
}
