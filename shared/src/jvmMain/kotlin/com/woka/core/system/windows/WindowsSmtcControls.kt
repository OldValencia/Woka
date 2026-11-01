package com.woka.core.system.windows

import androidx.compose.ui.awt.ComposeWindow
import com.woka.core.system.MediaCommand
import com.woka.core.system.MediaPlaybackState
import com.woka.core.system.MediaSessionInfo
import com.woka.core.system.SystemMediaControls
import java.awt.Frame
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.util.concurrent.Executors

private const val IID_INTEROP = "ddb0472d-c911-4a1f-86d9-dc3d71a95f5a"
private const val IID_SMTC = "99fa3ff4-1742-42a6-902e-087d41f965ec"

// ISystemMediaTransportControls, after IInspectable's six.
private const val PUT_PLAYBACK_STATUS = 7
private const val GET_DISPLAY_UPDATER = 8
private const val PUT_IS_ENABLED = 11
private const val PUT_IS_PLAY_ENABLED = 13
private const val PUT_IS_STOP_ENABLED = 15
private const val PUT_IS_PAUSE_ENABLED = 17
private const val PUT_IS_PREVIOUS_ENABLED = 25
private const val PUT_IS_NEXT_ENABLED = 27
private const val ADD_BUTTON_PRESSED = 32

// MediaPlaybackStatus.
private const val STATUS_CLOSED = 0
private const val STATUS_PLAYING = 3
private const val STATUS_PAUSED = 4

/**
 * The OS media session on Windows: System Media Transport Controls (AP-007), on Woka's
 * own top-level window. The first attempt hung it on a message-only window and every
 * `add_ButtonPressed` came back `CO_E_NOT_SUPPORTED` (`system-media-controls.md`); a real
 * application window is the one lead that attempt left untried.
 *
 * Every WinRT call runs on one worker thread, initialised once. The handler is agile and
 * is called on WinRT's own threads.
 */
internal class WindowsSmtcControls : SystemMediaControls {

    override var onCommand: (MediaCommand) -> Unit = {}

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "woka-smtc").apply { isDaemon = true } }
    private var smtc: MemorySegment = MemorySegment.NULL
    private val handler = SmtcButtonHandler(::onButton)

    init {
        worker.execute { WinRt.initThread() }
    }

    override fun enable() = onWorker("enable") {
        if (smtc == MemorySegment.NULL) smtc = open()
        putBool(PUT_IS_ENABLED, true)
        putBool(PUT_IS_PLAY_ENABLED, true)
        putBool(PUT_IS_PAUSE_ENABLED, true)
        putBool(PUT_IS_STOP_ENABLED, true)
    }

    override fun setSession(info: MediaSessionInfo) = onWorker("setSession") {
        if (smtc == MemorySegment.NULL) return@onWorker
        putBool(PUT_IS_NEXT_ENABLED, info.hasNext)
        putBool(PUT_IS_PREVIOUS_ENABLED, info.hasPrevious)
        SmtcDisplay.show(smtc, GET_DISPLAY_UPDATER, info)
    }

    override fun setPlayback(state: MediaPlaybackState) = onWorker("setPlayback") {
        if (smtc == MemorySegment.NULL) return@onWorker
        putInt(PUT_PLAYBACK_STATUS, if (state.isPlaying) STATUS_PLAYING else STATUS_PAUSED)
    }

    override fun disable() = onWorker("disable") {
        if (smtc == MemorySegment.NULL) return@onWorker
        putInt(PUT_PLAYBACK_STATUS, STATUS_CLOSED)
        putBool(PUT_IS_ENABLED, false)
    }

    override fun dispose() {
        onWorker("dispose") {
            WinRt.release(smtc)
            smtc = MemorySegment.NULL
        }
        worker.shutdown()
    }

    /** `ISystemMediaTransportControlsInterop::GetForWindow` on Woka's main window, and the handler. */
    private fun open(): MemorySegment = Arena.ofConfined().use { arena ->
        val hwnd = mainWindowHandle()
        val interop = WinRt.factory("Windows.Media.SystemMediaTransportControls", WinRt.guid(arena, IID_INTEROP))
        val out = arena.allocate(ADDRESS)
        val hr = WinRt.call(
            interop, 6, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
            MemorySegment.ofAddress(hwnd), WinRt.guid(arena, IID_SMTC), out
        ) as Int
        WinRt.release(interop)
        check(hr >= 0) { "GetForWindow failed: ${WinRt.hex(hr)}" }
        val controls = out.get(ADDRESS, 0)
        val token = arena.allocate(8)
        val added = WinRt.call(
            controls, ADD_BUTTON_PRESSED, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS),
            handler.pointer, token
        ) as Int
        check(added >= 0) { "add_ButtonPressed failed: ${WinRt.hex(added)}" }
        controls
    }

    private fun mainWindowHandle(): Long =
        Frame.getFrames().filterIsInstance<ComposeWindow>().first { it.title == "Woka" }.windowHandle

    private fun onButton(button: Int) {
        val command = when (button) {
            0 -> MediaCommand.Play
            1 -> MediaCommand.Pause
            2 -> MediaCommand.Stop
            6 -> MediaCommand.Next
            7 -> MediaCommand.Previous
            else -> return
        }
        onCommand(command)
    }

    private fun putBool(slot: Int, value: Boolean) =
        WinRt.callChecked(smtc, slot, "slot $slot", listOf(JAVA_BYTE), (if (value) 1 else 0).toByte())

    private fun putInt(slot: Int, value: Int) =
        WinRt.callChecked(smtc, slot, "slot $slot", listOf(JAVA_INT), value)

    private fun onWorker(what: String, block: () -> Unit) {
        worker.execute {
            try {
                block()
            } catch (e: Throwable) {
                System.err.println("SMTC $what failed: ${e.message}")
            }
        }
    }
}
