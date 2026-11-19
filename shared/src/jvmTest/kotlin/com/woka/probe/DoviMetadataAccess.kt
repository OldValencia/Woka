package com.woka.probe

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_DOVI_METADATA
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_side_data
import org.bytedeco.ffmpeg.global.avutil.avutil_version
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_DOUBLE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.nio.file.Path

/** DV5-001: separate research library, with copied data and a bounded lifetime. */
internal class DoviMetadataAccess(path: Path) : AutoCloseable {
    private val arena = Arena.ofConfined()
    private val read = try {
        val library = SymbolLookup.libraryLookup(path, arena)
        Linker.nativeLinker().downcallHandle(
            library.find("woka_dovi_metadata").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG)
        )
    } catch (failure: Throwable) {
        arena.close()
        throw failure
    }
    private val output = arena.allocate(JAVA_DOUBLE, 704)

    fun read(frame: AVFrame): DoubleArray {
        val side = av_frame_get_side_data(frame, AV_FRAME_DATA_DOVI_METADATA)
        check(side != null && !side.isNull) { "No parsed Dolby Vision metadata on frame" }
        val address = MemorySegment.ofAddress(side.data().address())
        val status = read.invokeExact(address, side.size(), avutil_version(), output, 704L) as Int
        check(status == 0) { "Dolby Vision metadata accessor rejected frame: $status" }
        return output.toArray(JAVA_DOUBLE)
    }

    override fun close() = arena.close()
}
