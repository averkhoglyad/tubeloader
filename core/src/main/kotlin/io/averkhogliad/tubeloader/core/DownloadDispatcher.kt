package io.averkhogliad.tubeloader.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

class DownloadDispatcher(
    private val scope: CoroutineScope,
    private val mediaTool: MediaTool,
    private val transition: (TaskId, DownloadState) -> Unit,
    private val taskIdGenerator: TaskIdGenerator = RandomTaskIdGenerator,
) {
    private val jobs = mutableMapOf<TaskId, Job>()
    private val overwriteConfirmations = ConcurrentHashMap<TaskId, CompletableDeferred<Boolean>>()
    private val toolLock = Mutex()
    private var toolReady = false

    fun submit(targetPath: Path, work: suspend (Path) -> Unit): TaskId {
        val taskId = taskIdGenerator.next()
        val confirmation = CompletableDeferred<Boolean>()
        overwriteConfirmations[taskId] = confirmation
        transition(taskId, DownloadState.Queued)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val part = partialFilePath(targetPath, taskId)
            var overwriteApproved = false
            try {
                if (Files.exists(targetPath)) {
                    overwriteApproved = awaitOverwriteConfirmation(taskId, targetPath, confirmation)
                    if (!overwriteApproved) {
                        deleteQuietly(part)
                        transition(taskId, DownloadState.Cancelled)
                        return@launch
                    }
                }
                ensureToolReady()
                Files.createFile(part)
                transition(taskId, DownloadState.Downloading(Progress.Indeterminate))
                work(part)
                transition(taskId, DownloadState.Finalizing)
                if (!overwriteApproved && Files.exists(targetPath)) {
                    overwriteApproved = awaitOverwriteConfirmation(taskId, targetPath, confirmation)
                    if (!overwriteApproved) {
                        deleteQuietly(part)
                        transition(taskId, DownloadState.Cancelled)
                        return@launch
                    }
                }
                val options = if (overwriteApproved) {
                    arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    arrayOf(StandardCopyOption.ATOMIC_MOVE)
                }
                Files.move(part, targetPath, *options)
                transition(taskId, DownloadState.Completed)
            } catch (cancellation: CancellationException) {
                deleteQuietly(part)
                transition(taskId, DownloadState.Cancelled)
                throw cancellation
            } catch (failure: Exception) {
                deleteQuietly(part)
                transition(taskId, DownloadState.Failed(failure))
            } finally {
                jobs.remove(taskId)
                overwriteConfirmations.remove(taskId)
            }
        }
        jobs[taskId] = job
        job.start()
        return taskId
    }

    fun confirmOverwrite(taskId: TaskId, overwrite: Boolean) {
        overwriteConfirmations[taskId]?.complete(overwrite)
    }

    fun cancel(taskId: TaskId) {
        jobs[taskId]?.cancel()
    }

    private suspend fun awaitOverwriteConfirmation(
        taskId: TaskId,
        targetPath: Path,
        confirmation: CompletableDeferred<Boolean>,
    ): Boolean {
        transition(taskId, DownloadState.Interrupted(PendingInteraction.OverwriteConfirmation(targetPath)))
        return confirmation.await()
    }

    private suspend fun ensureToolReady() {
        toolLock.withLock {
            if (!toolReady) {
                mediaTool.initialize()
                toolReady = true
            }
        }
    }

    private fun partialFilePath(target: Path, taskId: TaskId): Path =
        target.resolveSibling("${target.fileName}.part-$taskId")

    private fun deleteQuietly(part: Path) {
        try {
            Files.deleteIfExists(part)
        } catch (_: Exception) {
        }
    }
}
