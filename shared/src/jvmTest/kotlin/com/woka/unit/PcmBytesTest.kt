package com.woka.unit

import com.woka.feature.viewer.video.engine.audio.toPcmBytes
import java.nio.ShortBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals

class PcmBytesTest {

    @Test
    fun `a frame shorter than the reused buffer converts only its own samples`() {
        val buffer = ShortBuffer.wrap(shortArrayOf(0x0102, -2, 0x7FFF, 9))
        buffer.limit(3)
        assertContentEquals(byteArrayOf(0x02, 0x01, -2, -1, -1, 0x7F), buffer.toPcmBytes())
    }
}
