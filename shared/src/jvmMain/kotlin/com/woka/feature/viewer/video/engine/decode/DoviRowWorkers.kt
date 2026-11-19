package com.woka.feature.viewer.video.engine.decode

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Eight workers shared by viewer, lanes, previews and export; no per-grabber thread pools. */
internal object DoviRowWorkers {
    private const val WORKERS = 8
    private val pool = Executors.newFixedThreadPool(WORKERS) { action ->
        Thread(action, "woka-dovi-colour").apply { isDaemon = true }
    }

    /** Native pointers remain borrowed until every task ends, including interruption/failure. */
    fun render(height: Int, rows: (Int, Int) -> Unit) {
        val tasks = ArrayList<Future<Unit>>(WORKERS)
        var interrupted = false
        var failure: Throwable? = null
        try {
            repeat(WORKERS) { worker ->
                tasks += pool.submit(Callable { rows(height * worker / WORKERS, height * (worker + 1) / WORKERS) })
            }
        } finally {
            for (task in tasks) {
                var complete = false
                while (!complete) {
                    try {
                        task.get()
                        complete = true
                    } catch (_: InterruptedException) {
                        interrupted = true
                    } catch (error: ExecutionException) {
                        failure = failure ?: error.cause ?: error
                        complete = true
                    }
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
        failure?.let { throw it }
        if (interrupted) throw InterruptedException("Dolby Vision conversion interrupted")
    }
}
