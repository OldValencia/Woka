package com.woka.unit

import com.woka.feature.viewer.video.engine.decode.DoviRowWorkers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DoviRowWorkersTest {
    @Test
    fun `worker failure cannot release native pointers before all rows have finished`() = joined(false)

    @Test
    fun `owner interruption waits for borrowed native pointers and restores interruption`() = joined(true)

    private fun joined(interrupt: Boolean) {
        val owner = Executors.newSingleThreadExecutor()
        val ownerThread = AtomicReference<Thread>()
        val entered = CountDownLatch(8)
        val release = CountDownLatch(1)
        val finished = AtomicInteger()
        try {
            val result = owner.submit {
                ownerThread.set(Thread.currentThread())
                try {
                    DoviRowWorkers.render(8) { first, _ ->
                        entered.countDown()
                        if (!interrupt && first == 0) error("Rejected row")
                        check(release.await(5, SECONDS))
                        finished.incrementAndGet()
                    }
                } catch (error: InterruptedException) {
                    assertTrue(Thread.currentThread().isInterrupted)
                    throw error
                }
            }
            assertTrue(entered.await(5, SECONDS))
            if (interrupt) ownerThread.get().interrupt()
            // Real time: returning now would free the AVFrame under blocked native workers.
            assertFailsWith<TimeoutException> { result.get(100, MILLISECONDS) }
            release.countDown()
            val error = assertFailsWith<ExecutionException> { result.get(5, SECONDS) }
            if (interrupt) assertIs<InterruptedException>(error.cause) else assertIs<IllegalStateException>(error.cause)
            assertEquals(if (interrupt) 8 else 7, finished.get())
        } finally {
            release.countDown()
            owner.shutdown()
            assertTrue(owner.awaitTermination(5, SECONDS))
        }
    }
}
