package com.woka.core.system

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout

/**
 * Windows Unicode command-line recovery.
 *
 * On Windows, the standard Java launcher (including jpackage executables) converts command-line
 * arguments to `String[] args` using the system ANSI code page (`CP_ACP`, e.g. Windows-1251 or 1252).
 * Characters that cannot be represented in that code page (such as Japanese, Chinese, Arabic, or emojis)
 * are irreversibly replaced with '?' before reaching `main(args)`.
 *
 * This utility calls Win32 `GetCommandLineW` and `CommandLineToArgvW` to retrieve the original,
 * uncorrupted UTF-16 command line directly from the operating system.
 */
object WindowsCommandLine {

    private val isWindows: Boolean =
        System.getProperty("os.name").lowercase().contains("windows")

    /**
     * Attempts to read the raw Win32 command line as parsed by `CommandLineToArgvW`.
     * Returns null if not on Windows or if native calls fail.
     */
    fun getRawCommandLineArgs(): List<String>? {
        if (!isWindows) return null
        return try {
            readNativeArgs()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private fun readNativeArgs(): List<String>? {
        val linker = Linker.nativeLinker()
        val k32 = SymbolLookup.libraryLookup("Kernel32.dll", Arena.global())
        val shell32 = SymbolLookup.libraryLookup("Shell32.dll", Arena.global())

        val getCmdLineSym = k32.find("GetCommandLineW").orElse(null) ?: return null
        val cmdToArgvSym = shell32.find("CommandLineToArgvW").orElse(null) ?: return null
        val localFreeSym = k32.find("LocalFree").orElse(null) ?: return null

        val getCmdLineHandle = linker.downcallHandle(getCmdLineSym, FunctionDescriptor.of(ValueLayout.ADDRESS))
        val cmdToArgvHandle = linker.downcallHandle(
            cmdToArgvSym,
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        )
        val localFreeHandle = linker.downcallHandle(
            localFreeSym,
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        )

        val cmdLinePtr = getCmdLineHandle.invokeExact() as MemorySegment
        Arena.ofConfined().use { arena ->
            val numArgsPtr = arena.allocate(ValueLayout.JAVA_INT)
            val argvPtr = cmdToArgvHandle.invokeExact(cmdLinePtr, numArgsPtr) as MemorySegment
            if (argvPtr == MemorySegment.NULL) return null
            try {
                val count = numArgsPtr.get(ValueLayout.JAVA_INT, 0)
                val argv = argvPtr.reinterpret(count * ValueLayout.ADDRESS.byteSize())
                return (0 until count).map { i ->
                    readWideString(argv.getAtIndex(ValueLayout.ADDRESS, i.toLong()))
                }
            } finally {
                localFreeHandle.invokeExact(argvPtr) as MemorySegment
            }
        }
    }

    // Reads a null-terminated wide character (UTF-16) string from a native pointer.
    private fun readWideString(ptr: MemorySegment): String {
        val wide = ptr.reinterpret(Long.MAX_VALUE)
        val sb = StringBuilder()
        var index = 0L
        while (true) {
            val c = wide.getAtIndex(ValueLayout.JAVA_CHAR_UNALIGNED, index)
            if (c.code == 0) break
            sb.append(c)
            index++
        }
        return sb.toString()
    }

    /**
     * Resolves the startup arguments, recovering any Unicode characters that were corrupted
     * to '?' by Windows ANSI conversion.
     */
    fun resolveArgs(rawArgs: Array<String>): List<String> {
        val rawList = rawArgs.toList()
        if (!isWindows || rawList.isEmpty()) return rawList
        val win32Args = getRawCommandLineArgs() ?: return rawList
        return reconcile(rawList, win32Args)
    }

    /**
     * The application arguments are always the tail of the Win32 command line: after the exe
     * path for `Woka.exe`, after the JVM flags and main class for `java.exe`. The tail is taken
     * only when every pair agrees on its ASCII characters — the ones the ANSI conversion keeps —
     * otherwise the launcher's arguments are returned untouched.
     */
    internal fun reconcile(rawArgs: List<String>, win32Args: List<String>): List<String> {
        if (win32Args.size < rawArgs.size) return rawArgs
        val tail = win32Args.takeLast(rawArgs.size)
        val aligned = tail.zip(rawArgs).all { (win32Arg, rawArg) ->
            asciiSkeleton(win32Arg) == asciiSkeleton(rawArg)
        }
        return if (aligned) tail else rawArgs
    }

    // '?' is excluded too: it is what the conversion leaves for a lost character, and Windows
    // forbids it in file names, so it never carries meaning in a path.
    private fun asciiSkeleton(arg: String): String =
        arg.filter { it.code < 0x80 && it != '?' }
}
