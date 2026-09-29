package io.averkhogliad.tubeloader.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import kotlin.time.Instant

class CoreFacade(
    private val adapters: List<SourceAdapter>,
    initialConfig: AppConfig = AppConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)),
    private val workContext: CoroutineContext = Dispatchers.IO,
    private val taskIdGenerator: TaskIdGenerator = RandomTaskIdGenerator,
    private val clock: Clock = Clock.System,
) {
    private val sources: List<Source> = adapters.mapIndexed { index, adapter ->
        Source(SourceId(index), adapter.displayName)
    }

    val availableSources: List<Source> get() = sources

    private val adaptersBySourceId: Map<SourceId, SourceAdapter> =
        sources.zip(adapters).associate { (source, adapter) -> source.id to adapter }

    private val _states = MutableStateFlow<Map<TaskId, DownloadState>>(emptyMap())
    private val states: StateFlow<Map<TaskId, DownloadState>> = _states.asStateFlow()

    private val _config = MutableStateFlow(initialConfig)
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val queue = DownloadQueue(scope, workContext) { _config.value.maxParallelDownloads }

    suspend fun findByUrl(input: String): ResolveResult {
        val matches = mutableListOf<VideoRef>()
        var notFound = false
        for ((source, adapter) in sources.zip(adapters)) {
            when (val result = adapter.find(input)) {
                is FindResult.Found -> matches += VideoRef(source, result.id)
                FindResult.NotFound -> notFound = true
                FindResult.Unsupported -> Unit
            }
        }
        return when {
            matches.size > 1 -> error("Ambiguous match: ${matches.size} adapters claim input '$input'")
            matches.size == 1 -> ResolveResult.Resolved(matches.single())
            notFound -> ResolveResult.NotFound
            else -> ResolveResult.Unsupported
        }
    }

    suspend fun findById(sourceId: SourceId, id: String): ResolveResult {
        val adapter = adaptersBySourceId[sourceId]
            ?: error("Unknown source id: $sourceId")
        val source = sources[sourceId.index]
        return when (val result = adapter.find(id)) {
            is FindResult.Found -> ResolveResult.Resolved(VideoRef(source, result.id))
            FindResult.NotFound -> ResolveResult.NotFound
            FindResult.Unsupported -> ResolveResult.Unsupported
        }
    }

    suspend fun loadMeta(ref: VideoRef): LoadMetaResult {
        val adapter = adaptersBySourceId[ref.source.id]
            ?: error("Unknown source: ${ref.source.id}")
        return adapter.loadMeta(ref.videoId)
    }

    fun enqueue(ref: VideoRef, quality: Quality, targetPath: Path): DownloadHandle {
        val adapter = adaptersBySourceId[ref.source.id]
            ?: error("Unknown source: ${ref.source.id}")
        if (targetPath.parent == null) {
            error("targetPath must include a parent directory: $targetPath")
        }
        val startedAt = clock.now()
        val taskId = allocateTaskId(startedAt)
        val job = queue.submit { runDownload(taskId, adapter, ref.videoId, quality, targetPath) }
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                transition(taskId, DownloadStatus.Cancelled)
            }
        }
        return DownloadHandle(taskId, statesFor(taskId)) {
            transition(taskId, DownloadStatus.Cancelling)
            job.cancel()
        }
    }

    fun setConfig(config: AppConfig) {
        _config.value = config
        queue.limitChanged()
    }

    private suspend fun runDownload(
        taskId: TaskId,
        adapter: SourceAdapter,
        videoId: String,
        quality: Quality,
        targetPath: Path,
    ) {
        val part = partialFilePath(targetPath, taskId)
        try {
            transition(taskId, DownloadStatus.LoadingMeta)
            // without a suspension point the phase is conflated away for a collector on another thread
            yield()
            Files.createFile(part)
            transition(taskId, DownloadStatus.Downloading)
            val outcome = adapter.downloadVideo(videoId, quality, part) { source ->
                updateProgress(taskId, source.toProgress())
            }
            if (outcome is DownloadResult.Failed) {
                deleteQuietly(part)
                transition(taskId, DownloadStatus.Failed(outcome.error))
                return
            }
            transition(taskId, DownloadStatus.Finalizing)
            // a cancel landing in this window would otherwise go unnoticed and the file would be moved anyway
            coroutineContext.ensureActive()
            Files.move(
                part,
                targetPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            transition(taskId, DownloadStatus.Completed)
        } catch (cancellation: CancellationException) {
            deleteQuietly(part)
            throw cancellation
        } catch (failure: Exception) {
            deleteQuietly(part)
            transition(taskId, DownloadStatus.Failed(DownloadError.ExtractorBroken))
        }
    }

    private fun allocateTaskId(startedAt: Instant): TaskId {
        repeat(MAX_TASK_ID_ATTEMPTS) {
            val candidate = taskIdGenerator.next()
            if (register(candidate, startedAt)) return candidate
        }
        error("TaskId generator produced an occupied id $MAX_TASK_ID_ATTEMPTS times in a row")
    }

    private fun register(taskId: TaskId, startedAt: Instant): Boolean {
        val initial = DownloadState(taskId, DownloadStatus.Queued, startedAt = startedAt)
        while (true) {
            val current = _states.value
            if (taskId in current) return false
            if (_states.compareAndSet(current, current + (taskId to initial))) return true
        }
    }

    private fun transition(taskId: TaskId, status: DownloadStatus) {
        _states.update { states ->
            val current = states[taskId] ?: return@update states
            if (current.status.isTerminal) return@update states
            states + (taskId to current.copy(
                status = status,
                finishedAt = if (status.isTerminal) clock.now() else current.finishedAt,
            ))
        }
    }

    private fun updateProgress(taskId: TaskId, progress: Progress) {
        _states.update { states ->
            val current = states[taskId] ?: return@update states
            states + (taskId to current.copy(progress = progress))
        }
    }

    private fun statesFor(taskId: TaskId): Flow<DownloadState> = states.mapNotNull { it[taskId] }

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

private val DownloadStatus.isTerminal: Boolean
    get() = this is DownloadStatus.Completed || this is DownloadStatus.Cancelled || this is DownloadStatus.Failed

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
