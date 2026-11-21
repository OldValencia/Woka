package com.woka.core.system.windows

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT

/** `IID_ID3D12Device`. */
private const val IID_D3D12_DEVICE = "189819f1-1db6-4b57-be54-1821339b85f7"

/** `D3D_FEATURE_LEVEL_11_0`, the floor skia's Direct3D backend asks for. */
private const val FEATURE_LEVEL_11_0 = 0xb000

/**
 * One D3D12 device on the default adapter, made while the window is still being built, so
 * the GPU vendor's driver is loaded and initialised before skiko makes the window's own
 * device on the event thread. Over FFM, like [WinRt].
 *
 * It replaces what Java2D's Direct3D 9 pipeline used to do by accident: that start-up
 * (~110 ms on the prewarm thread, which `application {}` then waited for) loaded the
 * driver too, which is why turning it off alone made the first frame later. Measured
 * 2026-11-19 (ST-001), 5 alternating launches: shell +326 → +214 ms, JPEG +453 → +346 ms
 * after `main`.
 *
 * The device is held until [release], after the window's first frames. Let go at once, the
 * last reference to the driver went with it: on a start fast enough to finish before skiko
 * began (the native image), NVIDIA's loader was unloaded and loaded twice more, each time
 * re-verifying its DLL signatures (~60 ms of `imagehlp` apiece, ETW 2026-11-20). Held, the
 * native image's JPEG came at ~187 ms instead of ~318 ms; peak memory unchanged.
 */
object D3d12Prewarm {

    /** The prewarmed device, held so the driver stays loaded until skiko has its own. */
    private var device: MemorySegment? = null
    private var released = false

    /** False when there is no D3D12 or the call failed — never throws. */
    fun run(): Boolean = runCatching {
        // Global: unloading d3d12.dll before skiko loads it would undo the point.
        val d3d12 = SymbolLookup.libraryLookup("d3d12.dll", Arena.global())
        val create = Linker.nativeLinker().downcallHandle(
            d3d12.find("D3D12CreateDevice").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS)
        )
        Arena.ofConfined().use { arena ->
            val out = arena.allocate(ADDRESS)
            val iid = WinRt.guid(arena, IID_D3D12_DEVICE)
            val hr = create.invoke(MemorySegment.NULL, FEATURE_LEVEL_11_0, iid, out) as Int
            if (hr >= 0) keep(out.get(ADDRESS, 0))
            hr >= 0
        }
    }.getOrDefault(false)

    /** Lets the prewarmed device go; safe to call any number of times, from any thread. */
    @Synchronized
    fun release() {
        released = true
        device?.let(WinRt::release)
        device = null
    }

    /** A release that came first wins: the device is let go at once. */
    @Synchronized
    private fun keep(made: MemorySegment) {
        if (released) WinRt.release(made) else device = made
    }
}
