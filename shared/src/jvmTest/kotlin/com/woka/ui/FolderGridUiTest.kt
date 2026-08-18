package com.woka.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.core.data.ViewLayout
import com.woka.feature.folder.FolderGrid
import com.woka.feature.folder.FolderOverlayState
import com.woka.feature.folder.FolderViewModel
import com.woka.feature.folder.FolderZoom
import com.woka.support.MainDispatcherRule
import com.woka.support.awaitTrue
import com.woka.support.onUiWithFolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real `FolderGrid` over a real `FolderViewModel` and a real directory: a
 * click lands on a card, goes through the ViewModel, and comes back as selection
 * or as an open request.
 *
 * **No `testTag` was added to production.** A cell needs none — `FolderCard` is
 * `clickable`, which merges descendant semantics, so the title it draws *is* the
 * card node. The container gets one only because the grid is a bare
 * `LazyVerticalGrid` with no text, no click action and no content description, and
 * that tag is handed in through `FolderGrid`'s own `modifier` parameter **from
 * this file** — the shipped tree is untouched.
 *
 * Ctrl+A is *not* here. `selectAll` exists on the ViewModel and `FolderSelectionTest`
 * covers it, but nothing in the grid binds a key to it: it is reachable only from
 * the dock's select-mode accessory, which is the app shell rather than this screen.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class FolderGridUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun ComposeUiTest.showGrid(
        viewModel: FolderViewModel,
        onOpenMedia: (String) -> Unit = {}
    ) {
        setContent {
            val state by viewModel.state.collectAsState()
            FolderGrid(
                state = state,
                viewModel = viewModel,
                gridState = rememberLazyGridState(),
                zoom = FolderZoom(ViewLayout.Grid, zoomIn = {}, zoomOut = {}),
                path = viewModel.path,
                focusRequester = remember { FocusRequester() },
                overlays = remember { FolderOverlayState() },
                onNavigateToFolder = {},
                onOpenMedia = onOpenMedia,
                onEditMedia = {},
                modifier = Modifier.fillMaxSize().testTag(GRID)
            )
        }
    }

    /** Enters select mode and waits for the combine to publish it. */
    private fun ComposeUiTest.enterSelectMode(viewModel: FolderViewModel) {
        viewModel.toggleSelectMode()
        awaitTrue("select mode to reach the state") { viewModel.state.value.isSelectMode }
        waitForIdle()
    }

    @Test
    fun `every item in the listing gets a cell`() =
        onUiWithFolder(listOf("a.jpg", "b.png", "c.mp4")) { viewModel, _ ->
            showGrid(viewModel)
            onNodeWithText("a.jpg").assertIsDisplayed()
            onNodeWithText("b.png").assertIsDisplayed()
            onNodeWithText("c.mp4").assertIsDisplayed()
            assertEquals(3, viewModel.state.value.items.size)
        }

    @Test
    fun `an empty folder still lays the grid out`() = onUiWithFolder(emptyList()) { viewModel, _ ->
        showGrid(viewModel)
        onNodeWithTag(GRID).assertIsDisplayed()
        assertTrue(
            onAllNodes(hasClickAction()).fetchSemanticsNodes().isEmpty(),
            "an empty listing drew a cell"
        )
    }

    @Test
    fun `a click in select mode selects the cell, and a second one drops it`() =
        onUiWithFolder(listOf("a.jpg", "b.png")) { viewModel, dir ->
            showGrid(viewModel)
            enterSelectMode(viewModel)

            onNodeWithText("a.jpg").performClick()
            val selected = dir.resolve("a.jpg").absolutePath
            awaitTrue("the click to reach the selection") {
                viewModel.state.value.selectedPaths == setOf(selected)
            }

            waitForIdle()
            onNodeWithText("a.jpg").performClick()
            awaitTrue("the second click to clear it") {
                viewModel.state.value.selectedPaths.isEmpty()
            }
        }

    @Test
    fun `a click outside select mode opens the media instead of selecting it`() =
        onUiWithFolder(listOf("a.jpg")) { viewModel, dir ->
            var opened: String? = null
            showGrid(viewModel) { opened = it }
            onNodeWithText("a.jpg").performClick()
            waitForIdle()
            assertEquals(dir.resolve("a.jpg").absolutePath, opened, "the cell did not open")
            assertTrue(viewModel.state.value.selectedPaths.isEmpty(), "it selected as well")
        }

    @Test
    fun `leaving select mode clears the selection`() =
        onUiWithFolder(listOf("a.jpg", "b.png")) { viewModel, _ ->
            showGrid(viewModel)
            enterSelectMode(viewModel)
            onNodeWithText("a.jpg").performClick()
            awaitTrue("a selection to exist") { viewModel.state.value.selectedPaths.isNotEmpty() }

            viewModel.toggleSelectMode()
            awaitTrue("select mode to end with an empty selection") {
                val state = viewModel.state.value
                !state.isSelectMode && state.selectedPaths.isEmpty()
            }
            waitForIdle()
            onNodeWithText("a.jpg").assertIsDisplayed()
        }
}

private const val GRID = "folder-grid"
