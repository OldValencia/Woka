package com.woka.feature.viewer.shell

import androidx.compose.runtime.Immutable
import com.woka.core.data.CarouselVisibility

/**
 * The three context values the viewer shell threads through its layers.
 *
 * Each bundles parameters that were passed one at a time and always change
 * together. Deliberately **not** one "viewer context" object: the shell's long
 * parameter lists are mostly independent inputs, and folding those into a bag
 * would hide the coupling rather than remove it.
 */

/**
 * Frame-level commands, unchanged from `MediaViewerScreen` down to the context
 * menu in `MediaViewerFrame` — four levels of pure pass-through.
 */
@Immutable
data class ViewerActions(
    val onEdit: () -> Unit,
    val onCopy: () -> Unit,
    val onInfo: () -> Unit
)

/** For surfaces with no context menu of their own, such as the fullscreen window. */
val NO_VIEWER_ACTIONS = ViewerActions(onEdit = {}, onCopy = {}, onInfo = {})

/**
 * Where the media sits: the semantic size mode plus the pan offset. Read by the
 * content layer, which turns the mode into an actual graphics scale.
 */
@Immutable
data class ViewerTransform(
    val sizeMode: SizeMode,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * The same placement expressed for the toolbar's blurred backdrop, which follows
 * the frame using the *resolved* scale rather than the size mode. A separate type
 * from [ViewerTransform] on purpose — merging them would give each consumer a
 * field it must ignore.
 */
@Immutable
data class BackdropTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

/** What the chrome around the media is doing. */
@Immutable
data class ViewerChromeState(
    val carouselMode: CarouselVisibility,
    val isFullscreen: Boolean
) {
    val isCarouselEnabled: Boolean
        get() = carouselMode != CarouselVisibility.HIDDEN
}
