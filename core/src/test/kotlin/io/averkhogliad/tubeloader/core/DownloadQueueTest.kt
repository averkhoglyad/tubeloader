package io.averkhogliad.tubeloader.core

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueTest : FreeSpec({

    "submit" - {

        "runs no more tasks at once than the current limit" {
            runTest {
                // given
                val queue = downloadQueue { 2 }
                val log = StartLog()

                // when
                (0..3).forEach { queue.submit(log.work(it)) }
                testScheduler.advanceUntilIdle()

                // then
                log.order shouldBe listOf(0, 1)

                // when the running tasks end, the freed slots admit the tasks that waited
                log.releaseAll()
                testScheduler.advanceUntilIdle()

                // then
                log.order shouldBe listOf(0, 1, 2, 3)
            }
        }

        "starts the waiting tasks in submission order" {
            runTest {
                // given
                val queue = downloadQueue { 1 }
                val log = StartLog()

                // when
                (0..2).forEach { queue.submit(log.work(it)) }
                testScheduler.advanceUntilIdle()

                // then
                log.order shouldBe listOf(0)

                // when
                log.release(0)
                testScheduler.advanceUntilIdle()

                // then
                log.order shouldBe listOf(0, 1)

                // when
                log.release(1)
                testScheduler.advanceUntilIdle()

                // then
                log.order shouldBe listOf(0, 1, 2)
            }
        }

        "runs the work in the given work context, not on the queue worker".config(
            // the test blocks its thread on purpose, so kotest needs to be able to interrupt it
            blockingTest = true,
            timeout = 30.seconds,
        ) {
            runTest {
                // given
                val workers = Executors.newFixedThreadPool(2)
                try {
                    val queue = downloadQueue({ 2 }, workers.asCoroutineDispatcher())
                    val inside = CountDownLatch(2)
                    val concurrent = AtomicInteger()
                    val maxConcurrent = AtomicInteger()
                    val work: suspend () -> Unit = {
                        maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), ::maxOf)
                        inside.countDown()
                        // both tasks must be inside at once; a single worker lets this wait expire and
                        // the assertion below fail instead of hanging the build
                        inside.await(2, TimeUnit.SECONDS)
                        concurrent.decrementAndGet()
                    }

                    // when
                    queue.submit(work)
                    queue.submit(work)
                    testScheduler.advanceUntilIdle()

                    // then
                    inside.await(2, TimeUnit.SECONDS) shouldBe true
                    maxConcurrent.get() shouldBe 2
                } finally {
                    workers.shutdownNow()
                }
            }
        }

        "frees the slot when the work throws" {
            runTest {
                // given
                val dispatcher = StandardTestDispatcher(testScheduler)
                val thrown = CompletableDeferred<Throwable>()
                val scope = CoroutineScope(
                    SupervisorJob() + dispatcher +
                        CoroutineExceptionHandler { _, throwable -> thrown.complete(throwable) },
                )
                val queue = DownloadQueue(scope, dispatcher) { 1 }
                val log = StartLog()

                // when
                queue.submit { throw IllegalStateException("boom") }
                queue.submit(log.work(1))
                testScheduler.advanceUntilIdle()

                // then
                thrown.await().message shouldBe "boom"
                log.order shouldBe listOf(1)
                log.releaseAll()
                testScheduler.advanceUntilIdle()
            }
        }

        "frees the slot when the running work is cancelled" {
            runTest {
                // given
                val queue = downloadQueue { 1 }
                val log = StartLog()
                val running = queue.submit(log.work(0))
                queue.submit(log.work(1))
                testScheduler.advanceUntilIdle()
                log.order shouldBe listOf(0)

                // when
                running.cancel()
                testScheduler.advanceUntilIdle()

                // then
                log.order shouldBe listOf(0, 1)
                log.releaseAll()
                testScheduler.advanceUntilIdle()
            }
        }

        "frees the slot of a cancelled task that is still winding down".config(
            // the work blocks a real worker thread, so kotest needs to be able to interrupt the test
            blockingTest = true,
            timeout = 30.seconds,
        ) {
            runTest {
                // given
                val workers = Executors.newFixedThreadPool(2)
                val blocked = CountDownLatch(1)
                try {
                    val queue = downloadQueue({ 1 }, workers.asCoroutineDispatcher())
                    val inside = CountDownLatch(1)
                    val windingDown = queue.submit {
                        inside.countDown()
                        blocked.await(30, TimeUnit.SECONDS)
                    }
                    testScheduler.advanceUntilIdle()
                    inside.await(2, TimeUnit.SECONDS) shouldBe true

                    // when the task is cancelled while its work still blocks a worker thread, its
                    // finally cannot run yet, so the slot it holds is only freed by the queue itself
                    windingDown.cancel()
                    val admitted = CountDownLatch(1)
                    queue.submit { admitted.countDown() }
                    testScheduler.advanceUntilIdle()

                    // then
                    admitted.await(2, TimeUnit.SECONDS) shouldBe true
                } finally {
                    // release the blocked work and let it leave the await on its own: shutdownNow would
                    // interrupt it mid-await and surface an InterruptedException as a test failure
                    blocked.countDown()
                    workers.shutdown()
                    workers.awaitTermination(5, TimeUnit.SECONDS)
                }
            }
        }

        "skips a waiter that was cancelled before it got a slot" {
            runTest {
                // given
                val queue = downloadQueue { 1 }
                val log = StartLog()
                queue.submit(log.work(0))
                val waiting = queue.submit(log.work(1))
                queue.submit(log.work(2))
                testScheduler.advanceUntilIdle()
                log.order shouldBe listOf(0)

                // when the running task ends and the cancelled waiter is still in the queue
                log.release(0)
                waiting.cancel()
                testScheduler.advanceUntilIdle()

                // then the cancelled waiter is passed over instead of taking the freed slot
                log.order shouldBe listOf(0, 2)
                log.releaseAll()
                testScheduler.advanceUntilIdle()
            }
        }
    }

    "limitChanged" - {

        "starts the waiting tasks when the limit is raised" {
            runTest {
                // given
                var limit = 1
                val queue = downloadQueue { limit }
                val log = StartLog()
                (0..2).forEach { queue.submit(log.work(it)) }
                testScheduler.advanceUntilIdle()
                log.order shouldBe listOf(0)

                // when
                limit = 3
                queue.limitChanged()
                testScheduler.advanceUntilIdle()

                // then
                log.order shouldBe listOf(0, 1, 2)
                log.releaseAll()
                testScheduler.advanceUntilIdle()
            }
        }

        "does not preempt the running tasks when the limit is lowered" {
            runTest {
                // given
                var limit = 2
                val queue = downloadQueue { limit }
                val log = StartLog()
                (0..3).forEach { queue.submit(log.work(it)) }
                testScheduler.advanceUntilIdle()
                log.order shouldBe listOf(0, 1)

                // when
                limit = 1
                queue.limitChanged()
                testScheduler.advanceUntilIdle()

                // then the running tasks are left alone and no new slot is handed out
                log.order shouldBe listOf(0, 1)
                log.releaseAll()
                testScheduler.advanceUntilIdle()
                log.order shouldBe listOf(0, 1, 2, 3)
            }
        }
    }
})

/** Records the order the queue started the work in, and holds every started task until it is released. */
private class StartLog {
    private val gates = mutableMapOf<Int, CompletableDeferred<Unit>>()
    private var open = false
    val order = mutableListOf<Int>()

    fun work(index: Int): suspend () -> Unit = {
        order += index
        if (!open) gate(index).await()
    }

    fun release(index: Int) {
        gate(index).complete(Unit)
    }

    /** Lets every task finish, including the ones the queue starts later. */
    fun releaseAll() {
        open = true
        gates.values.forEach { it.complete(Unit) }
    }

    private fun gate(index: Int) = gates.getOrPut(index) { CompletableDeferred() }
}

private fun TestScope.downloadQueue(limit: () -> Int): DownloadQueue {
    val dispatcher = StandardTestDispatcher(testScheduler)
    return DownloadQueue(CoroutineScope(SupervisorJob() + dispatcher), dispatcher, limit)
}

private fun TestScope.downloadQueue(limit: () -> Int, workContext: CoroutineContext): DownloadQueue =
    DownloadQueue(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), workContext, limit)
