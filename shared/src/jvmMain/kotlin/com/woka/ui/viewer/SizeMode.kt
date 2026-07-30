package com.woka.ui.viewer

import com.woka.data.ScalePreset
import com.woka.data.SettingsRepository
import com.woka.data.WokaSettings

/**
 * Semantic source of truth for the media size state.
 * [Zoom] carries an integer percentage relative to native pixels (100 = 1:1),
 * [Preset] carries one of the auto-size presets. Zoom(100) and Preset(Original)
 * render identically, which is the switch-over point between the two modes.
 */
sealed interface SizeMode {
    data class Zoom(val percent: Int) : SizeMode
    data class Preset(val preset: ScalePreset) : SizeMode

    companion object {
        const val MIN_PERCENT = 10
        const val MAX_PERCENT = 1000
        const val DEFAULT_PERCENT = 100

        fun clampPercent(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
    }
}

/** The size state a freshly opened video should start in, per user settings. */
fun startSizeMode(settings: WokaSettings): SizeMode =
    if (settings.defaultUseZoom) SizeMode.Zoom(SizeMode.clampPercent(settings.defaultZoomPercent))
    else SizeMode.Preset(settings.scalePreset)

/** Writes the given size state back as the new default (used when "remember" is on). */
fun persistSizeState(repo: SettingsRepository, mode: SizeMode) {
    when (mode) {
        is SizeMode.Zoom -> {
            repo.setDefaultUseZoom(true)
            repo.setDefaultZoomPercent(mode.percent)
        }
        is SizeMode.Preset -> {
            repo.setDefaultUseZoom(false)
            repo.setScalePreset(mode.preset)
        }
    }
}
