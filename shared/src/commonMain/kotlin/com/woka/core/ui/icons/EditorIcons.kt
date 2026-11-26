package com.woka.core.ui.icons

import woka.shared.generated.resources.*

/**
 * The glyphs the editor asks for — the adjustments chooser, Crop's orientation pair, and
 * the two actions that set the sliders rather than being one.
 *
 * **An abstract base rather than a second namespace**, so `Icons.Static.Exposure` goes on
 * meaning what it always did: these are inherited members of [Icons.Static], not a place
 * callers have to know about. Split out 2026-09-02 when `Icons.kt` passed the size
 * mandate, along the one seam it has — every name here belongs to `feature/editor/`, and
 * everything left belongs to the app.
 *
 * Named for what the control **is**, never for the symbol it happens to use, so swapping
 * the art is a change to one line here rather than to a tool.
 */
abstract class EditorIcons {

    // The adjustments chooser, one glyph per slider (ME-144). Named for what the
    // control *is*, not for the symbol it happens to use, so swapping the art is
    // a change to one line here rather than to the tool.
    val Exposure = Res.drawable.plusminus_circle
    val Brightness = Res.drawable.sun_max
    val Contrast = Res.drawable.circle_lefthalf_filled
    val BlackPoint = Res.drawable.circle_bottomhalf_filled
    val Saturation = Res.drawable.drop
    val Vibrance = Res.drawable.sparkles
    val Warmth = Res.drawable.thermometer_sun
    val Tint = Res.drawable.eyedropper_halffull

    // The four ME-162 adds. Highlights and Shadows are a metaphor pair rather than
    // more of the `circle.*half.filled` family above, chosen on screen: the halves
    // read as geometry, and what these two do is light and dark.
    val Highlights = Res.drawable.sun_max_circle
    val Shadows = Res.drawable.moon_circle
    val Sharpness = Res.drawable.camera_filters
    val Noise = Res.drawable.aqi_medium

    /** Held down, the editor draws the picture as shot (ME-173). */
    val PeekOriginal = Res.drawable.paintbrush_slash

    // The editor's header (RD-201): undo, redo, and the inspector's show/hide (F6).
    val Undo = Res.drawable.arrow_uturn_backward
    val Redo = Res.drawable.arrow_uturn_forward
    val PanelToggle = Res.drawable.sidebar_left

    // Transform's four presses and Crop's shapes, drawn as tiles (RD-203).
    val RotateLeft = Res.drawable.rotate_left
    val RotateRight = Res.drawable.rotate_right
    val FlipHorizontal = Res.drawable.flip_horizontal
    val FlipVertical = Res.drawable.flip_vertical
    // A spirit level for Straighten (RD-205): Settings → Editor, and its own tab with Transform off.
    val Straighten = Res.drawable.level
    val AspectFree = Res.drawable.aspect_free
    val AspectSquare = Res.drawable.aspect_square
    val AspectFourThree = Res.drawable.aspect_4_3
    val AspectThreeTwo = Res.drawable.aspect_3_2
    val AspectSixteenNine = Res.drawable.aspect_16_9

    // Crop's orientation, as a pair — a tall frame and a wide one (ME-144).
    val Portrait = Res.drawable.rectangle_portrait
    val Landscape = Res.drawable.rectangle

    // The Clip and Sound panels' rows and buttons (RD-302), the prototype's Phosphor glyphs.
    val Speed = Res.drawable.gauge
    val Volume = Res.drawable.speaker_high
    val Mute = Res.drawable.speaker_slash
    val FadeIn = Res.drawable.fade_in
    val FadeOut = Res.drawable.fade_out
    val Delete = Res.drawable.trash
    val More = Res.drawable.dots_three
}
