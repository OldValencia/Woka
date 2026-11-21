package com.woka.feature.viewer.video.engine.decode

import com.woka.core.system.BundledNatives
import com.woka.feature.viewer.video.engine.probe.UnsupportedVideoException
import org.bytedeco.ffmpeg.global.avutil.avutil_version
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle

/** DV5-102 ABI; a missing/old bridge is a capability failure before picture decoding. */
internal object DoviColourNative {
    private data class Calls(
        val version: MethodHandle,
        val create: MethodHandle,
        val destroy: MethodHandle,
        val prepare: MethodHandle,
        val rows: MethodHandle,
        val finish: MethodHandle
    )

    private val calls: Calls? by lazy {
        try {
            val library = BundledNatives.load("woka_dovi")
            val linker = Linker.nativeLinker()
            fun bind(name: String, signature: FunctionDescriptor): MethodHandle =
                linker.downcallHandle(library.find(name).orElseThrow { IllegalStateException(name) }, signature)
            Calls(
                bind("woka_p5_version", FunctionDescriptor.of(JAVA_INT, JAVA_INT)),
                bind("woka_p5_create", FunctionDescriptor.of(ADDRESS, JAVA_INT)),
                bind("woka_p5_destroy", FunctionDescriptor.ofVoid(ADDRESS)),
                bind("woka_p5_prepare", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT)),
                bind("woka_p5_rows", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT)),
                bind("woka_p5_finish", FunctionDescriptor.ofVoid(ADDRESS))
            )
        } catch (_: IllegalStateException) {
            null
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }

    val available: Boolean get() {
        val access = calls ?: return false
        return (access.version.invokeExact(avutil_version()) as Int) == 1
    }

    private fun access(): Calls = calls ?: throw UnsupportedVideoException(
        "Dolby Vision Profile 5 colour processing is unavailable. Update or reinstall Woka.")

    fun create(): MemorySegment {
        val context = access().create.invokeExact(avutil_version()) as MemorySegment
        if (context == MemorySegment.NULL) throw UnsupportedVideoException("Cannot initialise Dolby Vision Profile 5 processing.")
        return context
    }

    fun prepare(context: MemorySegment, picture: MemorySegment) {
        checkStatus(access().prepare.invokeExact(context, picture, avutil_version()) as Int)
    }

    fun rows(context: MemorySegment, pixels: MemorySegment, size: Long, stride: Int, first: Int, end: Int) {
        checkStatus(access().rows.invokeExact(context, pixels, size, stride, first, end) as Int)
    }

    fun finish(context: MemorySegment) { access().finish.invokeExact(context) }
    fun destroy(context: MemorySegment) { access().destroy.invokeExact(context) }

    private fun checkStatus(status: Int) {
        if (status == 0) return
        val reason = when (status) {
            -2 -> "The native library does not match this FFmpeg version."
            -3 -> "This frame has missing or invalid Dolby Vision metadata."
            -4 -> "This frame uses an unsupported Dolby Vision signal or chroma layout."
            else -> "This frame could not be converted safely ($status)."
        }
        throw UnsupportedVideoException("Dolby Vision Profile 5: $reason")
    }
}
