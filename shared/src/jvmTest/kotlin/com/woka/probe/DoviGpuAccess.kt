package com.woka.probe

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.avutil_version
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.nio.file.Path

/** DV5-104 opt-in access to the build DLL, before packaging/enabling the backend. */
internal class DoviGpuAccess(library: Path, val width: Int, val height: Int) : AutoCloseable {
    private val arena = Arena.ofConfined()
    private val symbols = SymbolLookup.libraryLookup(library, arena)
    private fun bind(name: String, descriptor: FunctionDescriptor) =
        Linker.nativeLinker().downcallHandle(symbols.find(name).orElseThrow(), descriptor)
    private val create = bind("woka_p5_create", FunctionDescriptor.of(ADDRESS, JAVA_INT))
    private val destroy = bind("woka_p5_destroy", FunctionDescriptor.ofVoid(ADDRESS))
    private val prepare = bind("woka_p5_prepare", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT))
    private val render = bind("woka_p5_gpu", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT))
    private val finish = bind("woka_p5_finish", FunctionDescriptor.ofVoid(ADDRESS))
    private val context = create.invokeExact(avutil_version()) as MemorySegment
    private val pixels = arena.allocate(width.toLong() * height * 4, 32)

    fun convert(frame: AVFrame): Int {
        check(context != MemorySegment.NULL)
        val status = prepare.invokeExact(context, MemorySegment.ofAddress(frame.address()), avutil_version()) as Int
        check(status == 0) { "Prepare failed: $status" }
        try {
            return render.invokeExact(context, pixels, pixels.byteSize(), width * 4, width, height) as Int
        } finally {
            finish.invokeExact(context)
        }
    }

    fun bytes(): ByteArray = pixels.toArray(JAVA_BYTE)

    override fun close() {
        destroy.invokeExact(context)
        arena.close()
    }
}
