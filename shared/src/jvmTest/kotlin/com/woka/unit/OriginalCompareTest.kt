package com.woka.unit

import com.woka.core.ui.components.OriginalCompare
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `Original`'s one state, shared by the viewer and the editor (RD-502, `initComparison`):
 * a click toggles, a hold shows and gives back what was there before it.
 */
class OriginalCompareTest {

    @Test
    fun `a click toggles`() {
        val compare = OriginalCompare()
        compare.toggle()
        assertTrue(compare.active)
        compare.toggle()
        assertFalse(compare.active)
    }

    @Test
    fun `a hold shows the original and its release returns to the edit`() {
        val compare = OriginalCompare()
        compare.hold(true)
        assertTrue(compare.active)
        compare.hold(false)
        assertFalse(compare.active)
    }

    @Test
    fun `a hold over a comparison already on leaves it on`() {
        val compare = OriginalCompare()
        compare.toggle()
        compare.hold(true)
        compare.hold(false)
        assertTrue(compare.active, "the release restored the edit, not what the hold began on")
    }

    /** A held key repeats its down; the second must not overwrite what the release restores. */
    @Test
    fun `the repeats of a held key are one hold`() {
        val compare = OriginalCompare()
        compare.hold(true)
        compare.hold(true)
        compare.hold(true)
        compare.hold(false)
        assertFalse(compare.active, "a repeat recorded the original as what to return to")
    }

    @Test
    fun `end turns it off and forgets a hold`() {
        val compare = OriginalCompare()
        compare.hold(true)
        compare.end()
        assertFalse(compare.active)
        compare.toggle()
        compare.hold(false)
        assertTrue(compare.active, "a release after end restored a hold end had dropped")
    }
}
