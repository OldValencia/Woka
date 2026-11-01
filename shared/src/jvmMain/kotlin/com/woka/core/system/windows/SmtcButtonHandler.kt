package com.woka.core.system.windows

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.atomic.AtomicInteger

private const val S_OK = 0
private const val E_NOINTERFACE = 0x80004002.toInt()

private const val IID_IUNKNOWN = "00000000-0000-0000-c000-000000000046"
private const val IID_IAGILEOBJECT = "94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90"

/**
 * `TypedEventHandler<SystemMediaTransportControls, SystemMediaTransportControlsButtonPressedEventArgs>`,
 * the parameterised delegate's IID as WinRT derives it.
 */
private const val IID_BUTTON_HANDLER = "0557e996-7b23-5bae-aa81-ea0d671143a4"

/**
 * The `ButtonPressed` delegate, a COM object assembled in memory (AP-007): an object whose
 * first word points at a four-slot vtable of upcall stubs — `QueryInterface`, `AddRef`,
 * `Release`, `Invoke`.
 *
 * It lives as long as the process: [Arena.global] owns the stubs and the object, and the
 * reference count is honoured but never frees anything. It is agile — WinRT may call it
 * from any thread — because it touches nothing but [onButton].
 *
 * [onButton] receives `SystemMediaTransportControlsButton`'s raw value.
 */
internal class SmtcButtonHandler(private val onButton: (Int) -> Unit) {

    private val references = AtomicInteger(1)
    private val arena = Arena.global()
    private val accepted = setOf(IID_IUNKNOWN, IID_IAGILEOBJECT, IID_BUTTON_HANDLER)

    /** The object to hand `add_ButtonPressed`. */
    val pointer: MemorySegment

    init {
        val linker = Linker.nativeLinker()
        val lookup = MethodHandles.lookup()
        fun stub(name: String, type: MethodType, descriptor: FunctionDescriptor): MemorySegment =
            linker.upcallStub(lookup.bind(this, name, type), descriptor, arena)

        val vtable = arena.allocate(ADDRESS, 4)
        val queryInterface = stub(
            "queryInterface",
            MethodType.methodType(Int::class.java, MemorySegment::class.java, MemorySegment::class.java, MemorySegment::class.java),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
        )
        val addRef = stub(
            "addRef",
            MethodType.methodType(Int::class.java, MemorySegment::class.java),
            FunctionDescriptor.of(JAVA_INT, ADDRESS)
        )
        val release = stub(
            "release",
            MethodType.methodType(Int::class.java, MemorySegment::class.java),
            FunctionDescriptor.of(JAVA_INT, ADDRESS)
        )
        val invoke = stub(
            "invoke",
            MethodType.methodType(Int::class.java, MemorySegment::class.java, MemorySegment::class.java, MemorySegment::class.java),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
        )
        listOf(queryInterface, addRef, release, invoke).forEachIndexed { i, fn ->
            vtable.setAtIndex(ADDRESS, i.toLong(), fn)
        }
        pointer = arena.allocate(ADDRESS)
        pointer.set(ADDRESS, 0, vtable)
    }

    @Suppress("unused") // reached through the vtable
    private fun queryInterface(self: MemorySegment, iid: MemorySegment, out: MemorySegment): Int {
        val asked = WinRt.guidText(iid)
        val target = out.reinterpret(ADDRESS.byteSize())
        if (asked !in accepted) {
            target.set(ADDRESS, 0, MemorySegment.NULL)
            return E_NOINTERFACE
        }
        references.incrementAndGet()
        target.set(ADDRESS, 0, pointer)
        return S_OK
    }

    @Suppress("unused")
    private fun addRef(self: MemorySegment): Int = references.incrementAndGet()

    @Suppress("unused")
    private fun release(self: MemorySegment): Int = references.decrementAndGet().coerceAtLeast(0)

    @Suppress("unused")
    private fun invoke(self: MemorySegment, sender: MemorySegment, args: MemorySegment): Int {
        try {
            // ISystemMediaTransportControlsButtonPressedEventArgs::get_Button, slot 6.
            Arena.ofConfined().use { local ->
                val out = local.allocate(JAVA_INT)
                val hr = WinRt.call(args, 6, FunctionDescriptor.of(JAVA_INT, ADDRESS), out) as Int
                if (hr >= 0) onButton(out.get(JAVA_INT, 0))
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return S_OK
    }
}
