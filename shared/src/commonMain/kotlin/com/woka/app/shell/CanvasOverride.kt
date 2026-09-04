package com.woka.app.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.StillColorInfo

/**
 * Who currently owns the picture the top bar's controls address.
 *
 * The editor draws **over** a viewer that is still mounted underneath (§4.1), so the
 * pill in the top bar belongs to the viewer while the picture belongs to the editor.
 * Claiming the accessory slot instead — which is what the editor did first — replaced
 * the pill with a file name, and took the zoom control away with it.
 *
 * So the pill stays where it is and this says what it should be pointing at. The
 * carousel eye and `Edit` mean nothing while editing and animate out of the same pill
 * rather than being swapped for a second one: an accessory replaced wholesale is a
 * different subtree at the same slot, which Compose disposes and rebuilds — nothing
 * to animate between.
 */
class CanvasOverrideState {

    /** An editor is open over the viewer, so the eye and `Edit` retract. */
    var editing by mutableStateOf(false)

    /** ...and it holds changes that are not in the file yet. */
    var edited by mutableStateOf(false)

    /** Where the size control's reads and writes go. Null: the viewer's own. */
    var zoom by mutableStateOf<CanvasZoom?>(null)

    /**
     * What colour the owner's picture was written in, for the `HDR ▾` control (ME-303).
     *
     * The same name and the same type as `EditorEnginePicture.sourceColor`, which is where
     * it comes from, and **SDR by default so an unpublished pill hides the control** rather
     * than offering one that moves nothing. Only [CanvasZoomPill] reads it — the grid→editor
     * path, where there is no viewer underneath to have answered already.
     */
    var sourceColor by mutableStateOf(StillColorInfo.Sdr)
}

/**
 * The size control's whole contract, in percent of native 1:1 — the scale
 * `ZoomScale.kt` already works in, so the pill needs no second model.
 *
 * [minPercent]/[maxPercent] are the owner's real limits rather than the viewer's: the
 * editor's floor is Fit, and a slider that travels somewhere the picture will not follow
 * is a broken control.
 */
@Immutable
class CanvasZoom(
    val percent: Int,
    val minPercent: Int,
    val maxPercent: Int,
    val containerSize: IntSize,
    val nativeSize: IntSize,
    val onPercent: (Int) -> Unit,
    val onReset: () -> Unit
)

val LocalCanvasOverride = compositionLocalOf { CanvasOverrideState() }
