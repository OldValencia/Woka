package com.woka.feature.viewer.video.engine.decode

import com.woka.core.system.BundledNatives
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG

/** Optional DV5-104 backend. An older/platform bridge keeps the verified CPU path. */
internal object DoviGpuNative {
    private val render by lazy {
        BundledNatives.load("woka_dovi").find("woka_p5_gpu").orElse(null)?.let {
            Linker.nativeLinker().downcallHandle(it,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT))
        }
    }

    fun render(context: MemorySegment, target: DoviPicture): Boolean {
        val call = render ?: return false
        val picture = target.picture
        val stride = picture.linesize(0)
        val pixels = MemorySegment.ofAddress(picture.data(0).address())
        val status = call.invokeExact(context, pixels, stride.toLong() * picture.height(), stride,
            picture.width(), picture.height()) as Int
        check(status >= 0) { "Invalid Dolby Vision GPU output ($status)" }
        return status == 0
    }
}
