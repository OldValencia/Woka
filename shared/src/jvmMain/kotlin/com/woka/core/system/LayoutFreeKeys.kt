package com.woka.core.system

import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint

/** `б` and `ю`: what the Russian layout writes on the `,` and `.` keys. */
private const val CYRILLIC_BE = 0x0431
private const val CYRILLIC_YU = 0x044E

/** AWT's extended key code for a character with no `VK_` constant of its own. */
private const val EXTENDED_UNICODE = 0x01000000

/**
 * The `,` / `.` key under the finger, whatever layout is on (RD-501, found by the author: a
 * frame step stopped working on the Russian layout).
 *
 * Letters keep their `VK_` codes on every Windows layout, so `S` or `F` never needed this. The
 * punctuation keys do not: AWT maps those by the character they type, and on the Russian
 * layout the keys labelled `<` and `>` type `б` and `ю`, so `Key.Comma` and `Key.Period` never
 * arrive. They are recognised here by that character — from Compose's code point, or from
 * AWT's extended key code when the press carries none. Every other key is returned as it came.
 */
fun KeyEvent.layoutFreeKey(): Key = layoutFreeKey(key, typedCodePoint())

/** The rule itself, over the key AWT named and the code point it typed (0 for none). */
fun layoutFreeKey(key: Key, typed: Int): Key {
    if (key == Key.Comma || key == Key.Period) return key
    return when (Character.toLowerCase(typed)) {
        CYRILLIC_BE -> Key.Comma
        CYRILLIC_YU -> Key.Period
        else -> key
    }
}

private fun KeyEvent.typedCodePoint(): Int =
    utf16CodePoint.takeIf { it > 0 }
        ?: awtEventOrNull?.extendedKeyCode
            ?.takeIf { it > EXTENDED_UNICODE }
            ?.minus(EXTENDED_UNICODE)
        ?: 0
