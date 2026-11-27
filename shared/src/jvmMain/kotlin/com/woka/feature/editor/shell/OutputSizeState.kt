package com.woka.feature.editor.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.tools.resize.CUSTOM_KEY
import com.woka.feature.editor.tools.resize.ORIGINAL_KEY
import com.woka.feature.editor.tools.resize.SizeChoice
import com.woka.feature.editor.tools.resize.SizeLimits
import com.woka.feature.editor.tools.resize.SizePreset
import com.woka.feature.editor.tools.resize.resizedFrame

/**
 * The size group of `Save your changes?` and `Create a copy` (RD-401, `dialogs.js`
 * `sizedOutputDialog`): which preset is chosen, what the two fields read, and whether they
 * move together.
 *
 * **The fields are always the truth on screen.** A preset fills them; typing in either one
 * selects `Custom`; `Keep proportions` links them through [frame]'s shape — the frame after
 * crop and turns, before any Resize. A preset is always allowed, `Original` included; only
 * typed pixels are held to [limits].
 *
 * [legacy] is a percentage the old Resize slider saved that no preset names: it opens as
 * `Custom` at its pixels, and saving without touching anything keeps the percentage itself.
 */
@Stable
internal class OutputSizeState(
    val frame: IntSize,
    val presets: List<SizePreset>,
    val limits: SizeLimits,
    saved: SizeChoice = SizeChoice.Original
) {
    var selected by mutableStateOf(ORIGINAL_KEY)
        private set
    var width by mutableStateOf("")
        private set
    var height by mutableStateOf("")
        private set
    var keepProportions by mutableStateOf(true)
        private set

    /** Bumped when `Custom` is clicked, so the group can focus `Width`. */
    var focusWidth by mutableIntStateOf(0)
        private set

    private var lastEdited = 0
    private var legacy: SizeChoice.Percent? = null

    init {
        val named = presets.firstOrNull { it.choice == saved }
        when {
            named != null -> pick(named.key)
            saved is SizeChoice.Custom -> fill(IntSize(saved.width, saved.height), CUSTOM_KEY, saved.keepProportions)
            saved is SizeChoice.Percent -> {
                fill(resizedFrame(saved.percent, frame), CUSTOM_KEY, keep = true)
                legacy = saved
            }
            else -> pick(ORIGINAL_KEY)
        }
    }

    /** A button of the row. A preset fills the fields; `Custom` keeps them and asks for focus. */
    fun pick(key: String) {
        legacy = null
        selected = key
        if (key == CUSTOM_KEY) {
            focusWidth++
            return
        }
        presets.firstOrNull { it.key == key }?.let { fill(it.size, key, keepProportions) }
        lastEdited = 0
    }

    /** Typed into `Width` ([axis] 0) or `Height` (1): `Custom`, and the other side follows if linked. */
    fun type(axis: Int, text: String) {
        legacy = null
        selected = CUSTOM_KEY
        lastEdited = axis
        if (axis == 0) width = text else height = text
        link()
    }

    /** `Keep proportions`. Checked again, the last edited side stays and the other follows. */
    fun lock(checked: Boolean) {
        legacy = null
        keepProportions = checked
        if (selected == CUSTOM_KEY) link()
    }

    /** Both fields as whole pixels, or null while either is not one. */
    val size: IntSize?
        get() {
            val w = width.trim().toIntOrNull() ?: return null
            val h = height.trim().toIntOrNull() ?: return null
            return IntSize(w, h)
        }

    /** False only for typed pixels the renderer refuses — the error shows and the button greys. */
    val valid: Boolean
        get() = selected != CUSTOM_KEY || size?.let(limits::accepts) == true

    /** What `Save` writes: the preset's choice, the typed pixels, or an untouched legacy percentage. */
    val choice: SizeChoice
        get() = legacy ?: presets.firstOrNull { it.key == selected }?.choice
            ?: size?.let { SizeChoice.Custom(it.width, it.height, keepProportions) } ?: SizeChoice.Original

    /** What `Create copy` renders at: null for `Original`, the pixels otherwise. */
    val exportSize: IntSize?
        get() = if (selected == ORIGINAL_KEY) null else size

    private fun fill(size: IntSize, key: String, keep: Boolean) {
        selected = key
        width = size.width.toString()
        height = size.height.toString()
        keepProportions = keep
    }

    private fun link() {
        if (!keepProportions) return
        val typed = (if (lastEdited == 0) width else height).trim().toIntOrNull()?.takeIf { it > 0 } ?: return
        val sides = if (lastEdited == 0) frame.width to frame.height else frame.height to frame.width
        val other = limits.linked(typed, sides.first, sides.second).toString()
        if (lastEdited == 0) height = other else width = other
    }
}
