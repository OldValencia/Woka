package com.woka.core.data

/**
 * How far the volume may go with Sound boost on — 300 %.
 *
 * Its own file rather than a line in [WokaSettings]: every layer between the slider and
 * the stored record has to agree on this number, so it is a rule about the range and not
 * a field of the record. Past it the samples are mostly clipped and a quiet recording is
 * mush rather than loud.
 */
const val MAX_MEDIA_VOLUME = 3f

/**
 * The top of the volume range right now.
 *
 * Every way the level moves — the slider, both scroll wheels and the arrow keys — clamps
 * against this, so none of them can disagree about where the range ends.
 */
fun volumeCeiling(soundBoost: Boolean): Float = if (soundBoost) MAX_MEDIA_VOLUME else 1f
