package com.woka.unit

import com.woka.feature.viewer.video.engine.probe.DoviConfiguration
import com.woka.feature.viewer.video.engine.probe.DoviConfigurationRead
import com.woka.feature.viewer.video.engine.probe.unsupportedDoviReason
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoviSupportTest {
    @Test
    fun `P5 is identified by profile even if colour tags are absent`() {
        val reason = unsupportedDoviReason(DoviConfigurationRead.Present(DoviConfiguration(5, 0)))
        assertTrue(assertNotNull(reason).contains("Profile 5"))
    }

    @Test
    fun `P5 becomes available only with its colour converter`() {
        val p5 = DoviConfigurationRead.Present(DoviConfiguration(5, 0))
        assertNull(unsupportedDoviReason(p5, p5Available = true))
        assertNotNull(unsupportedDoviReason(p5, p5Available = false))
    }

    @Test
    fun `compatible Dolby profiles and non Dolby streams retain their colour path`() {
        assertNull(unsupportedDoviReason(DoviConfigurationRead.Absent))
        for (config in listOf(DoviConfiguration(7, 6), DoviConfiguration(8, 1),
            DoviConfiguration(8, 4), DoviConfiguration(10, 1))) {
            assertNull(unsupportedDoviReason(DoviConfigurationRead.Present(config)))
        }
    }

    @Test
    fun `unreadable metadata does not pretend the stream has no Dolby information`() {
        val reason = unsupportedDoviReason(DoviConfigurationRead.Unavailable("Wrong native version"))
        assertTrue(assertNotNull(reason).contains("metadata can't be read"))
    }
}
