package io.averkhogliad.tubeloader.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadDispatcher(
    private val scope: CoroutineScope,
    private val mediaTool: MediaTool,
    private val transition: (TaskId, DownloadState) -> Unit,
) {
    private val jobs = mutableMapOf<TaskId, Job>()
    private val toolLock = Mutex()
    private var toolReady = false

    fun submit(work: suspend () -> Unit): TaskId {
        val taskId = TaskId(java.util.UUID.randomUUID().toString())
        transition(taskId, DownloadState.Queued)
        val job = scope.launch {
            try {
                ensureToolReady()
                transition(taskId, DownloadState.Downloading(Progress.Indeterminate))
                work()
                transition(taskId, DownloadState.Finalizing)
                transition(taskId, DownloadState.Completed)
            } catch (cancellation: CancellationException) {
                transition(taskId, DownloadState.Cancelled)
                throw cancellation
            } catch (failure: Exception) {
                transition(taskId, DownloadState.Failed(failure))
            } finally {
                jobs.remove(taskId)
            }
        }
        jobs[taskId] = job
        return taskId
    }

    fun cancel(taskId: TaskId) {
        jobs[taskId]?.cancel()
    }

    private suspend fun ensureToolReady() {
        toolLock.withLock {
            if (!toolReady) {
                mediaTool.initialize()
                toolReady = true
            }
        }
    }
}
