package com.woka.core.system

import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Set by the packaging-time training run: how long after the first picture to exit. */
const val AOT_TRAINING_EXIT_PROPERTY = "woka.aotTraining.exitMs"

/**
 * Ends the run that records the shipped AOT cache (`trainAotCache` in
 * `desktopApp/build.gradle.kts`, ST-001) once a picture is on screen.
 *
 * The JVM writes `-XX:AOTCacheOutput` only on an orderly exit, and the training
 * run has to have drawn a real picture first, or the cache holds no viewer classes.
 * Without the property this does nothing.
 */
object AotTraining {
    private val exitMs = System.getProperty(AOT_TRAINING_EXIT_PROPERTY)?.toLongOrNull()

    @Volatile
    private var armed = exitMs != null

    fun pictureDrawn() {
        val delay = exitMs ?: return
        if (!armed) return
        armed = false
        thread(name = "Woka-Aot-Training-Exit", isDaemon = true) {
            Thread.sleep(delay)
            exitProcess(0)
        }
    }
}
