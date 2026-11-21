package com.woka.probe

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.avutil_version
import org.bytedeco.javacpp.BytePointer
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandle
import java.nio.ByteBuffer
import java.nio.file.Path

/** DV5-101 research DLL only. Context/output stay owned by this decoder thread. */
internal class DoviColourAccess(path: Path, width: Int, height: Int, threads: Int) : AutoCloseable {
    private val arena = Arena.ofConfined()
    private var context = MemorySegment.NULL
    private var destroy: MethodHandle? = null
    private val convert: MethodHandle
    private val output: ByteBuffer
    private var picture: AVFrame? = null
    val outputPicture: AVFrame get() = checkNotNull(picture)

    init {
        try {
            val library = SymbolLookup.libraryLookup(path, arena)
            val linker = Linker.nativeLinker()
            fun bind(name: String, descriptor: FunctionDescriptor): MethodHandle =
                linker.downcallHandle(library.find(name).orElseThrow(), descriptor)
            destroy = bind("woka_p5_probe_destroy", FunctionDescriptor.ofVoid(ADDRESS))
            val create = bind("woka_p5_probe_create", FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT))
            convert = bind("woka_p5_probe_convert", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT))
            val pixels = bind("woka_p5_probe_pixels", FunctionDescriptor.of(ADDRESS, ADDRESS))
            context = create.invokeExact(width, height, threads) as MemorySegment
            check(context != MemorySegment.NULL) { "P5 research converter unavailable (AVX2/geometry)" }
            val pointer = pixels.invokeExact(context) as MemorySegment
            check(pointer != MemorySegment.NULL)
            output = pointer.reinterpret(width.toLong() * height * 4).asByteBuffer()
            picture = av_frame_alloc()
            check(picture != null && !picture!!.isNull)
            outputPicture.width(width).height(height).format(AV_PIX_FMT_RGBA)
            outputPicture.data(0, BytePointer(output)).linesize(0, width * 4)
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun convert(frame: AVFrame) {
        val status = convert.invokeExact(context, MemorySegment.ofAddress(frame.address()), avutil_version()) as Int
        check(status == 0) { "P5 research conversion rejected frame: $status" }
    }

    fun copyPixels(): ByteArray {
        val bytes = ByteArray(output.capacity())
        output.rewind()
        output.get(bytes)
        return bytes
    }

    override fun close() {
        try {
            picture?.let { av_frame_free(it) }
            picture = null
            if (context != MemorySegment.NULL) {
                checkNotNull(destroy).invokeExact(context)
                context = MemorySegment.NULL
            }
        } finally {
            if (arena.scope().isAlive) arena.close()
        }
    }
}
