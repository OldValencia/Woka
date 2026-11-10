package com.woka

import java.io.File
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant

/**
 * TEMPORARY (next_steps 1.0.4, cold start): where launch time goes, from the
 * process's own start to the first frame. Appends one line per mark to
 * `%TEMP%\woka-startup.log`; with `WOKA_STARTUP_EXIT=1` the app quits after the
 * first frame so launches can be timed back to back. Remove once measured.
 */
object StartupTrace {
    private val processStart: Instant? = ProcessHandle.current().info().startInstant().orElse(null)
    private val jvmStartMs = ManagementFactory.getRuntimeMXBean().startTime
    private val log = File(System.getProperty("java.io.tmpdir"), "woka-startup.log")
    private val run = System.currentTimeMillis()

    val exitAfterFirstFrame: Boolean = System.getenv("WOKA_STARTUP_EXIT") == "1"

    fun mark(name: String) {
        val now = Instant.now()
        val sinceProcess = processStart?.let { Duration.between(it, now).toMillis() } ?: -1
        val sinceJvm = now.toEpochMilli() - jvmStartMs
        log.appendText("$run\t$name\tprocess=${sinceProcess}ms\tjvm=${sinceJvm}ms\n")
    }
}
