package io.averkhogliad.tubeloader.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * FIFO queue of submitted work with one global limit on the tasks running at once.
 *
 * Owns execution only: it takes a launch lambda, starts it as a coroutine, hands out a slot by the
 * current limit and frees the slot when the task ends. Statuses, task ids and paths are none of its
 * business. Every mutation of its own structures happens inside a coroutine on the scope dispatcher,
 * which is confined to a single worker, so plain collections are safe without locks. The work itself
 * runs in [workContext] instead: a task that reads a blocking stream would otherwise hold the single
 * confined worker and serialize downloads that are meant to run in parallel.
 */
class DownloadQueue(
    private val scope: CoroutineScope,
    private val workContext: CoroutineContext,
    private val limitProvider: () -> Int,
) {
    private val waiting = ArrayDeque<Waiter>()
    private val running = ArrayDeque<Job>()

    fun submit(work: suspend () -> Unit): Job = scope.launch {
        val job = coroutineContext[Job]!!
        val waiter = Waiter(job, CompletableDeferred())
        waiting += waiter
        pump()
        try {
            waiter.gate.await()
            withContext(workContext) { work() }
        } finally {
            running.remove(job)
            waiting.remove(waiter)
            pump()
        }
    }

    /** Re-reads the limit, so work that waited for a slot starts when the limit grew. */
    fun limitChanged() {
        scope.launch { pump() }
    }

    private fun pump() {
        running.removeAll { !it.isActive }
        while (running.size < limitProvider() && waiting.isNotEmpty()) {
            val waiter = waiting.removeFirst()
            if (!waiter.job.isActive) continue
            running += waiter.job
            waiter.gate.complete(Unit)
        }
    }
}

private class Waiter(
    val job: Job,
    val gate: CompletableDeferred<Unit>,
)
