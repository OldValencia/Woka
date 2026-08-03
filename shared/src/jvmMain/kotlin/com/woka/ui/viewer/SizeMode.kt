package com.woka.ui.viewer

import com.woka.data.ScalePreset
import com.woka.data.SettingsRepository
import com.woka.data.WokaSettings

/**
 * Semantic source of truth for the media size state. Both variants are points on
 * the one zoom scale (see `ZoomScale.kt`), measured in percent of native 1:1:
 * [Zoom] is a free value, [Preset] is one that stays stuck to Fit / Fill / Actual
 * and is re-resolved whenever the panel changes size. There are no modes — the
 * distinction is only whether the value follows the panel or stands still.
 */
sealed interface SizeMode {
    data class Zoom(val percent: Int) : SizeMode
    data class Preset(val preset: ScalePreset) : SizeMode

    companion object {
        /** Native 1:1, i.e. [ScalePreset.Original] expressed as a percentage. */
        const val ACTUAL_PERCENT = 100
    }
}

/** The size state a freshly opened video should start in, per user settings. */
fun startSizeMode(settings: WokaSettings): SizeMode =
    if (settings.defaultUseZoom) SizeMode.Zoom(settings.defaultZoomPercent.coerceAtLeast(1))
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
