package com.woka.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The editor's room (RD-201): the colours the window takes while an editor is open, from the
 * prototype's `--e-*` tokens (`ai/mockups/design/woka/tokens.css`).
 *
 * **A room, not a scrim.** The inspector used to float over the photograph and read
 * [ViewerChrome]; it now stands beside the picture on its own panel, which follows the theme —
 * dark in the dark app, light in the light one. The header shares the room, which is why this
 * lives in `core` rather than in the editor: the app shell paints it too.
 */
data class RoomColors(
    val room: Color,
    val panel: Color,
    val well: Color,
    val well2: Color,
    val track: Color,
    val knob: Color,
    val hover: Color,
    val hoverStrong: Color,
    val line: Color,
    val lane: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    /** `Done`, the selected glyph, a playhead — a fill; dark text goes on it ([onAccent]). */
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    /** The orange as text on [panel]. */
    val accentText: Color,
    val accentFill: Color,
    val accentLine: Color,
    val danger: Color,
    val sound: Color,
    val soundBg: Color,
    val switch: Color,
    val switchKnob: Color,
    val menu: Color,
    val tip: Color,
    val tipText: Color
)

val DarkRoom = RoomColors(
    room = Color(0xFF0B0B0D),
    panel = Color(0xFF131316),
    well = Color(0xFF1B1B1F),
    well2 = Color(0xFF25252A),
    track = Color(0xFF0C0C0E),
    knob = Color(0xFF2A2A30),
    hover = Color(0x0AFFFFFF), // rgba(255, 255, 255, 0.04)
    hoverStrong = Color(0x13FFFFFF), // rgba(255, 255, 255, 0.075)
    line = Color(0x12FFFFFF), // rgba(255, 255, 255, 0.07)
    lane = Color(0x06FFFFFF), // rgba(255, 255, 255, 0.025)
    text = Color(0xFFF2F2F4),
    muted = Color(0xFF8E8E97),
    faint = Color(0xFF56565E),
    accent = Color(0xFFFF9F0A),
    accentHover = Color(0xFFFFAD33),
    onAccent = Color(0xFF1C1C1E),
    accentText = Color(0xFFFFB340),
    accentFill = Color(0x24FF9F0A), // rgba(255, 159, 10, 0.14)
    accentLine = Color(0x99FF9F0A), // rgba(255, 159, 10, 0.6)
    danger = Color(0xFFFF6961),
    sound = Color(0xFF86B5AD),
    soundBg = Color(0xFF1A2725),
    switch = Color(0xFF3A3A41),
    switchKnob = Color(0xFFD9D9DE),
    menu = Color(0xFF1D1D21),
    tip = Color(0xFF2C2C31),
    tipText = Color(0xFFF2F2F4)
)

val LightRoom = RoomColors(
    room = Color(0xFFE8E8EB),
    panel = Color(0xFFF6F6F7),
    well = Color(0xFFFFFFFF),
    well2 = Color(0xFFEDEDF0),
    track = Color(0xFFE4E4E8),
    knob = Color(0xFFFFFFFF),
    hover = Color(0x08000000), // rgba(0, 0, 0, 0.03)
    hoverStrong = Color(0x0E000000), // rgba(0, 0, 0, 0.055)
    line = Color(0x13000000), // rgba(0, 0, 0, 0.075)
    lane = Color(0x08000000), // rgba(0, 0, 0, 0.03)
    text = Color(0xFF1C1C1E),
    muted = Color(0xFF6A6A72),
    faint = Color(0xFFA6A6AE),
    accent = DarkRoom.accent,
    accentHover = DarkRoom.accentHover,
    onAccent = DarkRoom.onAccent,
    accentText = Color(0xFFA85800),
    accentFill = Color(0x2BFF9F0A), // rgba(255, 159, 10, 0.17)
    accentLine = Color(0xA6D67800), // rgba(214, 120, 0, 0.65)
    danger = Color(0xFFD63A30),
    sound = Color(0xFF3D6F66),
    soundBg = Color(0xFFDCEBE7),
    switch = Color(0xFFD4D4DA),
    switchKnob = Color(0xFFFFFFFF),
    menu = Color(0xFFFFFFFF),
    tip = Color(0xFF1C1C1E),
    tipText = Color(0xFFF2F2F4)
)
