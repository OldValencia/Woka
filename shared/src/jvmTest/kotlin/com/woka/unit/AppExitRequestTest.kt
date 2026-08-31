package com.woka.unit

import com.woka.app.shell.AppExitRequest
import com.woka.app.shell.ExitVerdict
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The handshake between the exit doors and the editor (ME-160).
 *
 * A singleton whose state outlives any one test, which is what the reset is for.
 */
class AppExitRequestTest {

    @BeforeTest
    fun reset() = AppExitRequest.cancel()

    @Test
    fun `nothing is being asked at rest`() {
        assertFalse(AppExitRequest.requested)
        assertNull(AppExitRequest.verdict)
    }

    @Test
    fun `a request is unanswered until the editor answers it`() {
        AppExitRequest.request()

        assertTrue(AppExitRequest.requested)
        assertNull(AppExitRequest.verdict, "a request answered itself")

        AppExitRequest.answer(ExitVerdict.Save)
        assertEquals(ExitVerdict.Save, AppExitRequest.verdict)
    }

    /**
     * The regression this object exists for: a flag left true is a
     * `LaunchedEffect(requested)` that will not restart, and the second press of the
     * close button would do nothing with no way to find out why.
     */
    @Test
    fun `cancelling clears the request, so the next press asks again`() {
        AppExitRequest.request()
        AppExitRequest.answer(ExitVerdict.Leave)

        AppExitRequest.cancel()
        assertFalse(AppExitRequest.requested, "a cancelled request stayed up")
        assertNull(AppExitRequest.verdict, "a cancelled request kept its answer")

        AppExitRequest.request()
        assertTrue(AppExitRequest.requested)
        assertNull(AppExitRequest.verdict, "the second press inherited the first answer")
    }
}
