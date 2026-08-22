package com.woka.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorEngine

/**
 * An [EditorEngine] that draws a rectangle, so the shell can be driven with no real
 * medium behind it.
 *
 * `ImageEditorEngine.Surface` is `TODO("ME-103")` and `save` is `TODO("ME-104")`, so
 * `FixtureTool` alone cannot carry a shell test — a screen needs something to compose.
 * This is that something, and it stays useful afterwards: a shell test should fail
 * because the shell broke, not because a Skia decode did.
 */
class FixtureEngine(
    override val sourceSize: IntSize = IntSize(640, 480),
    override val kind: MediaKind = MediaKind.Image,
    override val capability: EditCapability = EditCapability.Recorded,
    private val result: EditSaveResult = EditSaveResult.NothingToDo,
    private val colour: Color = Color(0xFF3A5AA0),
    /**
     * What the canvas measures itself with, per document — null keeps [sourceSize],
     * which is every medium whose edits never change the frame.
     *
     * A real engine's answer moves once a crop lands, and the shell has to survive
     * that: the canvas is measured with it while the tool that *produced* it is still
     * open. Fixing that (2026-08-22) needed a fixture that could tell the two sizes
     * apart, which this is.
     */
    private val drawnSizeOf: ((EditDocument) -> IntSize)? = null,
    /** How the source lands on what was drawn — identity unless a test says otherwise. */
    private val previewMatrixOf: ((EditDocument) -> Matrix)? = null
) : EditorEngine {

    override fun drawnSize(document: EditDocument): IntSize =
        drawnSizeOf?.invoke(document) ?: sourceSize

    override fun previewMatrix(document: EditDocument): Matrix =
        previewMatrixOf?.invoke(document) ?: Matrix()

    private val drawn = mutableListOf<EditDocument>()

    /**
     * Every document this was asked to draw, in order — how a test proves the canvas
     * re-rendered rather than that a state flow merely changed.
     *
     * Copied out, so a caller cannot append to the record it is asserting on.
     */
    val documentsDrawn: List<EditDocument> get() = drawn.toList()

    /** Every document `Done` handed over. There is no target to record any more (§5). */
    val saveRequests = mutableListOf<EditDocument>()

    @Composable
    override fun Surface(document: EditDocument, modifier: Modifier) {
        // `SideEffect`, not a bare `drawn +=`: composition can run, be thrown away and
        // run again, and a list mutated in the composable body would count the
        // discarded passes. This runs only for a composition that was applied.
        SideEffect { drawn += document }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colour)
                .testTag(SURFACE_TAG)
        )
    }

    /** How many times the canvas asked for playback to start or stop. */
    var playbackToggles = 0
        private set

    override fun togglePlayback() {
        playbackToggles++
    }

    private val thumbnails = mutableListOf<EditDocument>()

    /**
     * Every document this was asked to draw **small** — how a test proves a preview
     * strip asked for one thumbnail per option and not one per option per frame.
     */
    val thumbnailsDrawn: List<EditDocument> get() = thumbnails.toList()

    @Composable
    override fun Thumbnail(document: EditDocument, modifier: Modifier) {
        SideEffect { thumbnails += document }
        Box(modifier = modifier.background(colour).testTag(THUMBNAIL_TAG))
    }

    override suspend fun save(document: EditDocument): EditSaveResult {
        saveRequests += document
        return result
    }

    companion object {
        const val SURFACE_TAG = "editor-surface"
        const val THUMBNAIL_TAG = "editor-thumbnail"
    }
}
