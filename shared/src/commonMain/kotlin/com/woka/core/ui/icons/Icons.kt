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
    object Static : EditorIcons() {
        val Add = Res.drawable.add
        val Close = Res.drawable.close
        val Folder = Res.drawable.folder
        val Pin = Res.drawable.pin
        val Play = Res.drawable.play
        val PlayFill = Res.drawable.play_fill
        val Pause = Res.drawable.pause
        val SkipForward5 = Res.drawable.skip_forward_5_clockwise
        val SkipBackward5 = Res.drawable.skip_backward_5_counterclockwise
        val SpeakerSlashFill = Res.drawable.speaker_slash_fill
        val SpeakerWave1Fill = Res.drawable.speaker_wave_1_fill
        val SpeakerWave2Fill = Res.drawable.speaker_wave_2_fill
        val SpeakerWave3Fill = Res.drawable.speaker_wave_3_fill

        /** Sound boost — Phosphor's `speaker-high` and `lightning`, filled, side by side. */
        val SpeakerBoltFill = Res.drawable.speaker_bolt_fill
        val Image = Res.drawable.image
        val Video = Res.drawable.videocam
        val MusicNote = Res.drawable.music_note
        val ChevronRight = Res.drawable.keyboard_arrow_right
        val Collapse = Res.drawable.collapse_content
        val Expand = Res.drawable.expand_content
        val Search = Res.drawable.magnifyingglass
        val Filter = Res.drawable.line_3_horizontal_decrease
        val Settings = Res.drawable.settings
        val Sort = Res.drawable.arrow_up_arrow_down

        val PencilLine = Res.drawable.pencil_line
        val Pencil = Res.drawable.pencil

        val Export = Res.drawable.export

        /**
         * **Auto's**, not Styles' (ME-174). A wand means "make this better on its own",
         * which is what Auto does; Styles offers a look to choose from and now wears
         * [Palette] instead. Two tools on one glyph is the mistake `EditorRail` records
         * having made twice.
         */
        val MagicWand = Res.drawable.magic_wand
        val Palette = Res.drawable.paintpalette

        /**
         * One parameter back to its detent, and all of them back to theirs — the same
         * counter-clockwise turn in both, so the pair reads as one idea at two scopes.
         */
        val Reset = Res.drawable.arrow_trianglehead_counterclockwise
        val ResetAll = Res.drawable.slider_horizontal_2_arrow_trianglehead_counterclockwise

        // The five markup shapes, for the chip row that used to spell them out
        val ShapeArrow = Res.drawable.arrow_up_right
        val ShapeRectangle = Res.drawable.rectangle
        val ShapeEllipse = Res.drawable.circle
        val ShapeFreehand = Res.drawable.scribble
        // The pen that draws a caption is a **T** (Phosphor `text-t`), not the `Aa` it
        // used to be (ME-155): `Aa` is now the button that sets the caption's faces, and
        // two adjacent controls drawing the same glyph read as one control.
        val ShapeText = Res.drawable.character_textbox

        // The four faces type can wear at once — the chip row beside `character_textbox`
        val TextBold = Res.drawable.bold
        val TextItalic = Res.drawable.italic
        val TextUnderline = Res.drawable.underline
        val TextStrikethrough = Res.drawable.strikethrough

        // New macOS style icons
        val ChevronDown = Res.drawable.chevron_down
        val Editor = Res.drawable.square_and_pencil
        val Trim = Res.drawable.scissors
        val Freeze = Res.drawable.snowflake
        val Crop = Res.drawable.crop
        val Adjust = Res.drawable.slash_circle
        val Navigation = Res.drawable.photo_on_rectangle_angled
        val ChevronRightMac = Res.drawable.chevron_right
        val Eye = Res.drawable.eye
        val EyeSlash = Res.drawable.eye_slash
        val InfoCircle = Res.drawable.info_circle

        // Zoom & scaling controls (video player enhancements)
        val ChevronLeft = Res.drawable.chevron_left
        val ScaleFit = Res.drawable.rectangle_arrowtriangle_2_inward
        val ScaleFill = Res.drawable.rectangle_arrowtriangle_2_outward
        val ScaleOriginal = Res.drawable.aspectratio

        // Stream selection (subtitles)
        val Subtitles = Res.drawable.captions_bubble
        val SubtitlesOff = Res.drawable.captions_bubble_slash

        val Logo = Res.drawable.woka_logo
    }
}
