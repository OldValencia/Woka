package com.woka.feature.viewer.video.engine.probe

import com.woka.core.system.BundledNatives
import org.bytedeco.ffmpeg.global.avutil.avutil_version
import org.bytedeco.javacpp.BytePointer
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle

/**
 * Named C field access against pinned FFmpeg headers (`natives/dovi`). Kotlin never reads
 * offsets in FFmpeg's ABI-unstable record. The bridge checks size and libavutil version
 * before dereferencing. It has no FFmpeg dependency and allocates or retains nothing.
 * Loaded only for streams that actually carry DV configuration, not ordinary videos.
 */
internal object DoviNative {
    private sealed interface Binding {
        data class Ready(val read: MethodHandle) : Binding
        data class Missing(val reason: String) : Binding
    }

    private val binding: Binding by lazy {
        try {
            val symbol = BundledNatives.load("woka_dovi").find("woka_dovi_read")
            if (symbol.isEmpty) Binding.Missing("The Dolby Vision bridge has no configuration reader")
            else Binding.Ready(Linker.nativeLinker().downcallHandle(
                symbol.get(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT)
            ))
        } catch (failure: IllegalStateException) {
            Binding.Missing("The Dolby Vision bridge is not installed")
        } catch (failure: UnsatisfiedLinkError) {
            Binding.Missing("The Dolby Vision bridge could not be loaded")
        }
    }

    internal fun read(data: BytePointer?, size: Long, version: Int = avutil_version()): DoviConfigurationRead {
        val access = binding
        if (access is Binding.Missing) return DoviConfigurationRead.Unavailable(access.reason)
        access as Binding.Ready
        val address = data?.takeUnless { it.isNull }?.address() ?: 0L
        val value = access.read.invokeExact(MemorySegment.ofAddress(address), size, version) as Int
        return when {
            value < 0 -> DoviConfigurationRead.Unavailable(when (value) {
                -2 -> "The Dolby Vision bridge does not match this FFmpeg version"
                -3 -> "The Dolby Vision configuration version or flags are not supported"
                else -> "The Dolby Vision configuration is incomplete"
            })
            else -> DoviConfigurationRead.Present(DoviConfiguration((value shr 8) and 255, value and 255))
        }
    }
}
