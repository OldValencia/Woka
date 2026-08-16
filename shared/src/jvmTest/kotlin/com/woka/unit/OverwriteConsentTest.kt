package com.woka.unit

import com.woka.feature.editor.core.OverwriteConsent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The consent is session-scoped and in memory. The third case is the one that
 * matters: it is what proves nothing was persisted (§5).
 */
class OverwriteConsentTest {

    @Test
    fun `it asks by default`() {
        assertTrue(OverwriteConsent().shouldAsk())
    }

    @Test
    fun `it stops asking once told to remember`() {
        val consent = OverwriteConsent()
        consent.rememberForSession()
        assertFalse(consent.shouldAsk())
    }

    @Test
    fun `a freshly built instance asks again`() {
        OverwriteConsent().rememberForSession()
        // A new instance stands in for the next run of the app. If this ever fails,
        // the opt-out has found its way onto disk and destructive overwrite has
        // quietly become the default.
        assertTrue(OverwriteConsent().shouldAsk())
    }
}
