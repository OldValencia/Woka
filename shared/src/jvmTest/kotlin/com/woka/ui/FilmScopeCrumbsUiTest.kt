package com.woka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import com.woka.feature.editor.core.ClipScope
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.shell.EditorFrameControls
import com.woka.feature.editor.shell.EditorScopeLine
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import kotlin.test.BeforeTest
import kotlin.test.Test

private const val WHOLE_TAG = "editor-scope-whole"
private const val CLIP_TAG = "editor-scope-clip"
private const val SHAPE_TAG = "editor-scope-shape"

/** The film's crumbs for what RD-302 added: a still frame by that name, a Markup shape by its own. */
@OptIn(ExperimentalTestApi::class)
class FilmScopeCrumbsUiTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `a still frame and a Markup shape name themselves`() = onUi {
        val controls = EditorFrameControls(EditorFrames(clips = true), EditScope.AllFrames, 0, {}, {})
        var line by mutableStateOf(controls.copy(clip = ClipScope(number = 3, lengthMs = 2_000, still = true)))
        setContent { EditorScopeLine(line) {} }
        onNodeWithTag(CLIP_TAG).assertTextContains("Still frame", substring = true)
        line = controls.copy(shape = "Arrow")
        waitForIdle()
        onNodeWithTag(SHAPE_TAG).assertTextContains("Arrow")
        onNodeWithTag(WHOLE_TAG).assertIsDisplayed()
    }
}
