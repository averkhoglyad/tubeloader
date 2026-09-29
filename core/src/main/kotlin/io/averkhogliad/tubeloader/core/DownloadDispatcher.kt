package io.averkhogliad.tubeloader.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class DownloadDispatcher(
    private val scope: CoroutineScope,
    private val mediaTool: MediaTool,
    private val register: (TaskId) -> Boolean,
    private val transition: (TaskId, DownloadStatus) -> Unit,
    private val onProgress: (TaskId, Progress) -> Unit,
    private val taskIdGenerator: TaskIdGenerator = RandomTaskIdGenerator,
) {
    private val jobs = mutableMapOf<TaskId, Job>()
    private val overwriteConfirmations = ConcurrentHashMap<TaskId, CompletableDeferred<Boolean>>()
    private val toolLock = Mutex()
    private var toolReady = false

    fun submit(targetPath: Path, work: suspend (Path, (SourceProgress) -> Unit) -> DownloadResult): TaskId {
        val taskId = allocateTaskId()
        val part = partialFilePath(targetPath, taskId)
        val confirmation = CompletableDeferred<Boolean>()
        overwriteConfirmations[taskId] = confirmation
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var overwriteApproved = false
            try {
                if (Files.exists(targetPath)) {
                    overwriteApproved = awaitOverwriteConfirmation(taskId, targetPath, confirmation)
                    if (!overwriteApproved) {
                        deleteQuietly(part)
                        transition(taskId, DownloadStatus.Cancelled)
                        return@launch
                    }
                }
                ensureToolReady()
                Files.createFile(part)
                transition(taskId, DownloadStatus.Downloading)
                val outcome = work(part) { source ->
                    onProgress(taskId, source.toProgress())
                }
                when (outcome) {
                    DownloadResult.Success -> Unit
                    is DownloadResult.Failed -> {
                        deleteQuietly(part)
                        transition(taskId, DownloadStatus.Failed(outcome.error))
                        return@launch
                    }
                }
                transition(taskId, DownloadStatus.Finalizing)
                if (!overwriteApproved && Files.exists(targetPath)) {
                    overwriteApproved = awaitOverwriteConfirmation(taskId, targetPath, confirmation)
                    if (!overwriteApproved) {
                        deleteQuietly(part)
                        transition(taskId, DownloadStatus.Cancelled)
                        return@launch
                    }
                }
                val options = if (overwriteApproved) {
                    arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    arrayOf(StandardCopyOption.ATOMIC_MOVE)
                }
                // a cancel landing in this window would otherwise go unnoticed and the file would be moved anyway
                coroutineContext.ensureActive()
                Files.move(part, targetPath, *options)
                transition(taskId, DownloadStatus.Completed)
            } catch (cancellation: CancellationException) {
                // rethrown so the catch below does not turn cancellation into a failure
                throw cancellation
            } catch (failure: Exception) {
                deleteQuietly(part)
                transition(taskId, DownloadStatus.Failed(DownloadError.ExtractorBroken))
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                deleteQuietly(part)
                transition(taskId, DownloadStatus.Cancelled)
            }
            jobs.remove(taskId)
            overwriteConfirmations.remove(taskId)
        }
        jobs[taskId] = job
        job.start()
        return taskId
    }

    private fun allocateTaskId(): TaskId {
        repeat(MAX_TASK_ID_ATTEMPTS) {
            val candidate = taskIdGenerator.next()
            if (register(candidate)) return candidate
        }
        error("TaskId generator produced an occupied id $MAX_TASK_ID_ATTEMPTS times in a row")
    }

    fun confirmOverwrite(taskId: TaskId, overwrite: Boolean) {
        overwriteConfirmations[taskId]?.complete(overwrite)
    }

    fun cancel(taskId: TaskId) {
        val job = jobs[taskId] ?: return
        transition(taskId, DownloadStatus.Cancelling)
        job.cancel()
    }

    private suspend fun awaitOverwriteConfirmation(
        taskId: TaskId,
        targetPath: Path,
        confirmation: CompletableDeferred<Boolean>,
    ): Boolean {
        transition(taskId, DownloadStatus.Interrupted(PendingInteraction.OverwriteConfirmation(targetPath)))
        return confirmation.await()
    }

    private suspend fun ensureToolReady() {
        toolLock.withLock {
            if (!toolReady) {
                mediaTool.initialize().getOrThrow()
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

private const val MAX_TASK_ID_ATTEMPTS = 16

private const val FRACTION_SCALE = 1000L

private fun SourceProgress.toProgress(): Progress = when (this) {
    SourceProgress.Indeterminate -> Progress.Indeterminate
    is SourceProgress.Absolute ->
        if (total > 0 && processed in 0..total) Progress.Determinate(processed, total) else Progress.Indeterminate

    is SourceProgress.Fraction ->
        if (ratio.isFinite() && ratio in 0.0..1.0) {
            Progress.Determinate((ratio * FRACTION_SCALE).toLong(), FRACTION_SCALE)
        } else {
            Progress.Indeterminate
        }
}
