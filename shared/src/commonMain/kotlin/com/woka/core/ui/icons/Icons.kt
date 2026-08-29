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
        val ShapeText = Res.drawable.textformat

        // The four faces type can wear at once — the chip row beside `textformat` (ME-151)
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
    }
}
