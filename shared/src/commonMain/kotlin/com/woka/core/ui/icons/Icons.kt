package com.woka.core.ui.icons

import woka.shared.generated.resources.*


/**
 * A unified router for all application icons.
 * Follows the Apple style of separating static resources and dynamic/animated code-based symbols.
 */
object Icons {
    
    /**
     * Static SVG resources. Short-cuts for generated Res.drawable.
     */
    object Static {
        val Add = Res.drawable.add
        val Close = Res.drawable.close
        val Delete = Res.drawable.delete
        val Disabled = Res.drawable.disabled
        val Folder = Res.drawable.folder
        val Play = Res.drawable.play
        val PlayFill = Res.drawable.play_fill
        val Pause = Res.drawable.pause
        val SkipForward5 = Res.drawable.skip_forward_5_clockwise
        val SkipBackward5 = Res.drawable.skip_backward_5_counterclockwise
        val SpeakerFill = Res.drawable.speaker_fill
        val SpeakerSlashFill = Res.drawable.speaker_slash_fill
        val SpeakerWave1Fill = Res.drawable.speaker_wave_1_fill
        val SpeakerWave2Fill = Res.drawable.speaker_wave_2_fill
        val SpeakerWave3Fill = Res.drawable.speaker_wave_3_fill

        /** Sound boost — `speaker.fill` and `bolt.fill` side by side, at one height. */
        val SpeakerBoltFill = Res.drawable.speaker_bolt_fill
        val Home = Res.drawable.home
        val Image = Res.drawable.image
        val File = Res.drawable.file_present
        val Video = Res.drawable.videocam
        val Link = Res.drawable.link
        val ChevronRight = Res.drawable.keyboard_arrow_right
        val Collapse = Res.drawable.collapse_content
        val Expand = Res.drawable.expand_content
        val Search = Res.drawable.magnifyingglass
        val Clear = Res.drawable.xmark_circle_fill
        val Tag = Res.drawable.tag
        val Filter = Res.drawable.line_3_horizontal_decrease
        val MagnifyingGlassPlus = Res.drawable.magnifyingglass_plus
        val Settings = Res.drawable.settings
        val Grid = Res.drawable.square_grid_2x2
        val List = Res.drawable.list_bullet
        val Table = Res.drawable.tablecells
        val Sort = Res.drawable.arrow_up_arrow_down
        
        val Bell = Res.drawable.bell
        val BellFill = Res.drawable.bell_fill
        val BellBadge = Res.drawable.bell_badge
        val BellBadgeFill = Res.drawable.bell_badge_fill
        val BellSlash = Res.drawable.bell_slash
        val BellSlashFill = Res.drawable.bell_slash_fill
        val BellWaves = Res.drawable.bell_and_waves_left_and_right
        val BellWavesFill = Res.drawable.bell_and_waves_left_and_right_fill

        val CheckmarkCircle = Res.drawable.checkmark_circle
        val PencilLine = Res.drawable.pencil_line
        val Pencil = Res.drawable.pencil
        val PencilSlash = Res.drawable.pencil_slash

        val Export = Res.drawable.export
        val ExportDone = Res.drawable.export_done
        val ExportFail = Res.drawable.export_fail
        val ExportWait = Res.drawable.export_wait
        val Share = Res.drawable.share

        val MagicWand = Res.drawable.magic_wand

        // The five markup shapes, for the chip row that used to spell them out
        val ShapeArrow = Res.drawable.arrow_up_right
        val ShapeRectangle = Res.drawable.rectangle
        val ShapeEllipse = Res.drawable.circle
        val ShapeFreehand = Res.drawable.scribble
        // The pen that draws a caption is a **box with a letter in it**, not the `Aa` it
        // used to be (ME-155): `Aa` is now the button that sets the caption's faces, and
        // two adjacent controls drawing the same glyph read as one control.
        val ShapeText = Res.drawable.character_textbox

        // The four faces type can wear at once — the chip row beside `character_textbox`
        val TextBold = Res.drawable.bold
        val TextItalic = Res.drawable.italic
        val TextUnderline = Res.drawable.underline
        val TextStrikethrough = Res.drawable.strikethrough

        val Undo = Res.drawable.undo
        val Redo = Res.drawable.redo
        
        val NoSign = Res.drawable.nosign

        // New macOS style icons
        val SidebarLeft = Res.drawable.sidebar_left
        val ChevronDown = Res.drawable.chevron_down
        val ZoomOut = Res.drawable.minus_magnifyingglass
        val Editor = Res.drawable.square_and_pencil
        val Crop = Res.drawable.rectangle_arrowtriangle_2_inward
        val Adjust = Res.drawable.slash_circle
        val Navigation = Res.drawable.photo_on_rectangle_angled
        val ChevronRightMac = Res.drawable.chevron_right
        val Eye = Res.drawable.eye
        val EyeSlash = Res.drawable.eye_slash
        val InfoCircle = Res.drawable.info_circle
        val DocumentOnDocument = Res.drawable.document_on_document

        // Zoom & scaling controls (video player enhancements)
        val ChevronLeft = Res.drawable.chevron_left
        val ScaleFit = Res.drawable.rectangle_arrowtriangle_2_inward
        val ScaleFill = Res.drawable.rectangle_arrowtriangle_2_outward
        val ScaleOriginal = Res.drawable.aspectratio

        // Stream selection (subtitles)
        val Subtitles = Res.drawable.captions_bubble
        val SubtitlesOff = Res.drawable.captions_bubble_slash

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

        // Crop's orientation, as a pair — a tall frame and a wide one (ME-144).
        val Portrait = Res.drawable.rectangle_portrait
        val Landscape = Res.drawable.rectangle
    }
}
