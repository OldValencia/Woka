package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import com.woka.feature.editor.engine.video.PLAY_HEAD_SPLIT_TAG
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.viewer.video.engine.seek
import com.woka.support.MainDispatcherRule
import com.woka.support.onTimeline
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A clip's look by its menu (ME-267; the floating bar's `Copy` and `Paste ▾` until RD-301):
 * `Copy look` takes one clip's own work, `Paste look` and `Paste sound settings` put the kind
 * they name on another, and neither paste is offered before there is something of its kind.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ClipLookUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `a look copied from one clip pastes onto the other, kind by kind`() = onTimeline { holder, edits ->
        holder.state!!.seek(1_000L)
        waitForIdle()
        onNodeWithTag(PLAY_HEAD_SPLIT_TAG).performClick()
        waitForIdle()
        val (first, second) = TrimTool.clipsIn(edits.document)!!
        val look = ToolOp("adjust", ParamSet())
        edits.own = mapOf(first.key to listOf(look))

        onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { rightClick(centerLeft.copy(x = width * 0.1f, y = height * 0.3f)) }
        waitForIdle()
        onNodeWithText("Paste look").assertIsNotEnabled()
        onNodeWithText("Copy look").performClick()
        waitForIdle()

        onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { rightClick(centerLeft.copy(x = width * 0.8f, y = height * 0.3f)) }
        waitForIdle()
        // Only a look was copied: there is no sound to paste.
        onNodeWithText("Paste sound settings").assertIsNotEnabled()
        onNodeWithText("Paste look").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(second.key to listOf(look), edits.pasted)
        assertEquals(false, edits.pasted!!.second.any { it.toolId == SoundTool.manifest.id })
    }
}
