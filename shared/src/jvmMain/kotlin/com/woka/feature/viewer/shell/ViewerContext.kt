package com.woka.feature.viewer.shell

import androidx.compose.runtime.Immutable
import com.woka.core.data.CarouselVisibility
import com.woka.feature.editor.VIDEO_EDITING_ENABLED

/**
 * The three context values the viewer shell threads through its layers.
 *
 * Each bundles parameters that were passed one at a time and always change
 * together. Deliberately **not** one "viewer context" object: the shell's long
 * parameter lists are mostly independent inputs, and folding those into a bag
 * would hide the coupling rather than remove it.
 */

/** One extra right-click entry a surface contributes. */
@Immutable
data class ViewerMenuEntry(val label: String, val onClick: () -> Unit)

/**
 * Frame-level commands, unchanged from `MediaViewerScreen` down to the context
 * menu in `MediaViewerFrame` — four levels of pure pass-through.
 *
 * [showFileActions] exists because the fullscreen/PiP window has no file open in
 * the sense the three below mean: it used to be handed three no-op lambdas, and
 * rendered a menu in which every item did nothing. A null [onEdit] drops Edit alone.
 */
@Immutable
data class ViewerActions(
    val onEdit: (() -> Unit)?,
    val onCopy: () -> Unit,
    val onInfo: () -> Unit,
    val showFileActions: Boolean = true,
    val extra: List<ViewerMenuEntry> = emptyList()
)

/** For surfaces with no file actions of their own, such as the fullscreen window. */
val NO_VIEWER_ACTIONS =
    ViewerActions(onEdit = {}, onCopy = {}, onInfo = {}, showFileActions = false)

/**
 * The viewer's Edit, or null where none is offered: a video while
 * [VIDEO_EDITING_ENABLED] is false. Null hides the pill's button and the right-click entry.
 */
internal fun editActionOf(isVideo: Boolean, edit: () -> Unit): (() -> Unit)? =
    edit.takeIf { !isVideo || VIDEO_EDITING_ENABLED }

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
    val isFullscreen: Boolean,
    /** The ambient-fill setting; it only does anything while [isFullscreen]. */
    val ambientFill: Boolean = false
) {
    val isCarouselEnabled: Boolean
        get() = carouselMode != CarouselVisibility.HIDDEN
}
