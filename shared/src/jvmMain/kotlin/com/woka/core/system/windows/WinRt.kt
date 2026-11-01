package com.woka.core.system.windows

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.lang.invoke.MethodHandle

/** `RO_INIT_MULTITHREADED`: the session lives on a worker thread with no window of its own. */
private const val RO_INIT_MULTITHREADED = 1

/** `RPC_E_CHANGED_MODE`: the thread was already initialised, as the other apartment type. */
private const val RPC_E_CHANGED_MODE = 0x80010106.toInt()

/**
 * The few `combase.dll` calls WinRT needs, and calls through a COM vtable — over FFM, so
 * no JNI, no JNA and no native build (AP-007). Every downcall handle is built once.
 *
 * A COM object is a pointer to a pointer to its vtable; slot `n` is the n-th function
 * pointer, and every method takes the object itself as its first argument.
 */
internal object WinRt {

    private val linker = Linker.nativeLinker()
    private val combase = SymbolLookup.libraryLookup("combase.dll", Arena.global())

    private val roInitialize = downcall("RoInitialize", FunctionDescriptor.of(JAVA_INT, JAVA_INT))
    private val createString = downcall(
        "WindowsCreateString",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS)
    )
    private val deleteString = downcall("WindowsDeleteString", FunctionDescriptor.of(JAVA_INT, ADDRESS))
    private val activationFactory = downcall(
        "RoGetActivationFactory",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
    )

    /** One handle per method shape, called with the function pointer first. */
    private val shapes = HashMap<FunctionDescriptor, MethodHandle>()

    private fun downcall(name: String, descriptor: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(combase.find(name).orElseThrow(), descriptor)

    /** Initialises the calling thread for WinRT; an already initialised thread is fine. */
    fun initThread() {
        val hr = roInitialize.invoke(RO_INIT_MULTITHREADED) as Int
        check(hr >= 0 || hr == RPC_E_CHANGED_MODE) { "RoInitialize failed: ${hex(hr)}" }
    }

    /** An HSTRING for the length of [block], deleted after it. */
    fun <T> withHString(text: String, block: (MemorySegment) -> T): T = Arena.ofConfined().use { arena ->
        val chars = arena.allocateFrom(text, Charsets.UTF_16LE)
        val out = arena.allocate(ADDRESS)
        check(createString.invoke(chars, text.length, out) as Int >= 0) { "WindowsCreateString failed" }
        val hstring = out.get(ADDRESS, 0)
        try {
            block(hstring)
        } finally {
            deleteString.invoke(hstring)
        }
    }

    /** The activation factory of [className], as the interface [iid]. */
    fun factory(className: String, iid: MemorySegment): MemorySegment = withHString(className) { name ->
        Arena.ofConfined().use { arena ->
            val out = arena.allocate(ADDRESS)
            val hr = activationFactory.invoke(name, iid, out) as Int
            check(hr >= 0) { "RoGetActivationFactory($className) failed: ${hex(hr)}" }
            out.get(ADDRESS, 0)
        }
    }

    /**
     * Calls slot [slot] of [obj]'s vtable. [descriptor] lists the arguments after `this`;
     * the call returns whatever its return layout says — an HRESULT for most WinRT methods.
     */
    fun call(obj: MemorySegment, slot: Int, descriptor: FunctionDescriptor, vararg args: Any): Any {
        val vtable = obj.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0)
        val function = vtable.reinterpret((slot + 1) * ADDRESS.byteSize()).getAtIndex(ADDRESS, slot.toLong())
        val full = descriptor.insertArgumentLayouts(0, ADDRESS)
        val handle = shapes.getOrPut(full) { linker.downcallHandle(full) }
        return handle.invokeWithArguments(listOf(function, obj) + args)
    }

    /** A method returning an HRESULT, checked. */
    fun callChecked(
        obj: MemorySegment,
        slot: Int,
        what: String,
        layouts: List<MemoryLayout>,
        vararg args: Any
    ) {
        val hr = call(obj, slot, FunctionDescriptor.of(JAVA_INT, *layouts.toTypedArray()), *args) as Int
        check(hr >= 0) { "$what failed: ${hex(hr)}" }
    }

    /** `IUnknown::Release`. */
    fun release(obj: MemorySegment) {
        if (obj != MemorySegment.NULL) call(obj, 2, FunctionDescriptor.of(JAVA_INT))
    }

    /** A GUID in [arena], from its usual text form. */
    fun guid(arena: Arena, text: String): MemorySegment {
        val hex = text.replace("-", "")
        val segment = arena.allocate(16)
        segment.set(JAVA_INT, 0, hex.substring(0, 8).toLong(16).toInt())
        segment.set(JAVA_SHORT, 4, hex.substring(8, 12).toInt(16).toShort())
        segment.set(JAVA_SHORT, 6, hex.substring(12, 16).toInt(16).toShort())
        for (i in 0 until 8) {
            segment.set(JAVA_BYTE, 8L + i, hex.substring(16 + i * 2, 18 + i * 2).toInt(16).toByte())
        }
        return segment
    }

    /** The text form of a GUID, for logging what a caller asked for. */
    fun guidText(segment: MemorySegment): String {
        val s = segment.reinterpret(16)
        val tail = (8 until 16).joinToString("") { "%02x".format(s.get(JAVA_BYTE, it.toLong())) }
        return "%08x-%04x-%04x-%s-%s".format(
            s.get(JAVA_INT, 0), s.get(JAVA_SHORT, 4), s.get(JAVA_SHORT, 6), tail.take(4), tail.drop(4)
        )
    }

    fun hex(hr: Int): String = "0x%08X".format(hr)
}
