package com.woka.core.system.windows

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandle

private const val IID_RANDOM_ACCESS_STREAM = "905a0fe1-bc53-11df-8c49-001e4fc686da"
private const val IID_STREAM_REFERENCE_STATICS = "857309dc-3fbf-4e7d-986f-ef3b1a07a964"

/** `IRandomAccessStreamReferenceStatics::CreateFromStream`, after IInspectable's six and two siblings. */
private const val CREATE_FROM_STREAM = 8

/** `BSOS_DEFAULT`: the WinRT stream wraps the IStream as it is. */
private const val BSOS_DEFAULT = 0

/**
 * A cover for the Windows media panel (AP-007): PNG bytes in memory, made into the
 * `RandomAccessStreamReference` `put_Thumbnail` takes. No file is written — `SHCreateMemStream`
 * copies the bytes into an `IStream`, `CreateRandomAccessStreamOverStream` adapts it.
 *
 * The caller releases the returned reference.
 */
internal object SmtcThumbnail {

    private val linker = Linker.nativeLinker()

    private val memStream: MethodHandle = linker.downcallHandle(
        SymbolLookup.libraryLookup("shlwapi.dll", Arena.global()).find("SHCreateMemStream").orElseThrow(),
        FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT)
    )

    private val overStream: MethodHandle = linker.downcallHandle(
        SymbolLookup.libraryLookup("shcore.dll", Arena.global())
            .find("CreateRandomAccessStreamOverStream").orElseThrow(),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS)
    )

    fun reference(png: ByteArray): MemorySegment = Arena.ofConfined().use { arena ->
        val bytes = arena.allocate(png.size.toLong())
        MemorySegment.copy(png, 0, bytes, JAVA_BYTE, 0, png.size)
        val stream = memStream.invoke(bytes, png.size) as MemorySegment
        check(stream != MemorySegment.NULL) { "SHCreateMemStream failed" }
        try {
            val out = arena.allocate(ADDRESS)
            val iid = WinRt.guid(arena, IID_RANDOM_ACCESS_STREAM)
            val hr = overStream.invoke(stream, BSOS_DEFAULT, iid, out) as Int
            check(hr >= 0) { "CreateRandomAccessStreamOverStream failed: ${WinRt.hex(hr)}" }
            val randomAccess = out.get(ADDRESS, 0)
            try {
                val statics = WinRt.factory(
                    "Windows.Storage.Streams.RandomAccessStreamReference",
                    WinRt.guid(arena, IID_STREAM_REFERENCE_STATICS)
                )
                try {
                    val layouts = listOf(ADDRESS, ADDRESS)
                    WinRt.callChecked(statics, CREATE_FROM_STREAM, "CreateFromStream", layouts, randomAccess, out)
                    out.get(ADDRESS, 0)
                } finally {
                    WinRt.release(statics)
                }
            } finally {
                WinRt.release(randomAccess)
            }
        } finally {
            WinRt.release(stream)
        }
    }
}
