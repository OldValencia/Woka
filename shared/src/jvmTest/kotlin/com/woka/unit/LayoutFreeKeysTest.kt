package com.woka.unit

import androidx.compose.ui.input.key.Key
import com.woka.core.system.layoutFreeKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `,` / `.` on the Russian layout (RD-501, found by the author): AWT names the keys labelled
 * `<` and `>` by what they type there, `б` and `ю`, so no `Key.Comma` or `Key.Period` arrives
 * — the key is AWT's undefined one, and only the typed character tells them apart.
 */
class LayoutFreeKeysTest {

    /** AWT's `VK_UNDEFINED`, as Compose names it. */
    private val undefined = Key(0L)

    @Test
    fun `the Russian layout's б and ю are the comma and period keys`() {
        assertEquals(Key.Comma, layoutFreeKey(undefined, 'б'.code))
        assertEquals(Key.Period, layoutFreeKey(undefined, 'ю'.code))
        assertEquals(Key.Comma, layoutFreeKey(undefined, 'Б'.code), "Shift+Б is still the key")
    }

    @Test
    fun `the US layout and every other key come through as they are`() {
        assertEquals(Key.Comma, layoutFreeKey(Key.Comma, ','.code))
        assertEquals(Key.Period, layoutFreeKey(Key.Period, '.'.code))
        assertEquals(Key.S, layoutFreeKey(Key.S, 'ы'.code), "a letter keeps its key on any layout")
        assertEquals(undefined, layoutFreeKey(undefined, 0), "a press that typed nothing stays unnamed")
    }
}
