package com.woka

import androidx.compose.ui.awt.ComposeWindow
import com.woka.app.navigation.openExternalPath
import com.woka.core.data.WokaStorage
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * Single-instance coordination and dynamic port IPC across launches.
 *
 * Avoids hardcoded port collisions by picking an ephemeral port, recording it in
 * WokaStorage, and verifying identity with a handshake before forwarding paths.
 */
internal object SingleInstance {

    private const val HANDSHAKE = "WOKA-INSTANCE"
    private const val TIMEOUT_MS = 250

    private val portFile: File get() = File(WokaStorage.root, "lock.port")
    private var serverSocket: ServerSocket? = null
    private var activeWindow: ComposeWindow? = null

    fun startOrForward(args: Array<String>): Boolean {
        val port = readPort()
        if (port > 0 && signalExistingInstance(port, args)) return false
        return claimPrimaryInstance()
    }

    fun bindWindow(window: ComposeWindow) {
        activeWindow = window
    }

    private fun claimPrimaryInstance(): Boolean = try {
        val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        serverSocket = socket
        writePort(socket.localPort)
        startListening(socket)
        registerMacOpenFileHandler(::openPathInRunningWindow)
        true
    } catch (_: Exception) {
        true
    }

    private fun startListening(socket: ServerSocket) {
        thread(isDaemon = true, name = "Woka-IPC-Listener") {
            while (!socket.isClosed) {
                try {
                    handleClient(socket.accept())
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        thread(isDaemon = true, name = "Woka-IPC-Worker") {
            try {
                client.use {
                    it.soTimeout = TIMEOUT_MS
                    val writer = PrintWriter(OutputStreamWriter(it.getOutputStream(), StandardCharsets.UTF_8), true)
                    writer.println(HANDSHAKE)
                    val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.UTF_8))
                    val payload = reader.readLine()?.trim().orEmpty()
                    openPathInRunningWindow(payload)
                }
            } catch (_: Exception) {
                // Client connection closed or timed out
            }
        }
    }

    private fun signalExistingInstance(port: Int, args: Array<String>): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), TIMEOUT_MS)
            socket.soTimeout = TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            if (reader.readLine()?.trim() != HANDSHAKE) {
                portFile.delete()
                false
            } else {
                val clean = args
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotEmpty() && it != "%*" && it != "%1" }
                val payload = clean.joinToString(" ").trim().removeSurrounding("\"")
                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
                writer.println(payload)
                true
            }
        }
    } catch (_: Exception) {
        portFile.delete()
        false
    }

    private fun readPort(): Int = try {
        if (portFile.exists()) Files.readString(portFile.toPath()).trim().toInt() else -1
    } catch (_: Exception) {
        -1
    }

    private fun writePort(port: Int) = try {
        portFile.parentFile?.mkdirs()
        Files.writeString(portFile.toPath(), port.toString())
        portFile.deleteOnExit()
    } catch (_: Exception) {
        // Proceed without lock file
    }

    fun openPathInRunningWindow(path: String) {
        SwingUtilities.invokeLater {
            if (path.isNotEmpty()) openExternalPath(path)
            val window = activeWindow ?: return@invokeLater
            if (window.isMinimized) window.isMinimized = false
            window.toFront()
            window.requestFocus()
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
            portFile.delete()
        } catch (_: Exception) {
            // Socket already closed
        }
    }
}
