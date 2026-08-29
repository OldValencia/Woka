package com.woka.feature.viewer.shell

import com.woka.core.data.SettingsRepository

/**
 * Turning Sound boost on or off, wherever the volume popover is drawn.
 *
 * **Off also lowers the level**, and that is the whole reason this is a function rather
 * than a call to `setSoundBoost`: the repository pulls the *stored* level back to 100 %,
 * but each window holds its own copy of the live one — the screen a `mutableStateOf`, the
 * fullscreen window the player's own field — and a copy left at 240 % under a slider that
 * now stops at 100 % is a level nothing on screen could reach to turn down.
 *
 * [level] and [onLevel] are that copy, read and written by the caller that owns it.
 */
internal fun soundBoostSetter(
    settingsRepo: SettingsRepository,
    level: () -> Float,
    onLevel: (Float) -> Unit
): (Boolean) -> Unit = { enabled ->
    settingsRepo.setSoundBoost(enabled)
    if (!enabled) onLevel(level().coerceAtMost(1f))
}
