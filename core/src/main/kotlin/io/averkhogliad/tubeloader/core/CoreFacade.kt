package io.averkhogliad.tubeloader.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CoreFacade(
    private val adapters: List<SourceAdapter>,
    initialConfig: AppConfig = AppConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    taskIdGenerator: TaskIdGenerator = RandomTaskIdGenerator,
) {
    private val sources: List<Source> = adapters.mapIndexed { index, adapter ->
        Source(SourceId(index), adapter.displayName)
    }

    val availableSources: List<Source> get() = sources

    private val adaptersBySourceId: Map<SourceId, SourceAdapter> =
        sources.zip(adapters).associate { (source, adapter) -> source.id to adapter }

    private val _downloads = MutableStateFlow<Map<TaskId, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<TaskId, DownloadState>> = _downloads.asStateFlow()

    private val _config = MutableStateFlow(initialConfig)
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val dispatcher = DownloadDispatcher(scope, taskIdGenerator, ::register, ::transition, ::updateProgress)

    suspend fun findByUrl(input: String): ResolveResult {
        val matches = mutableListOf<Pair<Source, String>>()
        var notFound = false
        for ((source, adapter) in sources.zip(adapters)) {
            when (val result = adapter.find(input)) {
                is FindResult.Found -> matches += source to result.id
                FindResult.NotFound -> notFound = true
                FindResult.Unsupported -> Unit
            }
        }
        return when {
            matches.size > 1 -> error("Ambiguous match: ${matches.size} adapters claim input '$input'")
            matches.size == 1 -> {
                val (source, videoId) = matches.single()
                ResolveResult.Resolved(source, videoId)
            }
            notFound -> ResolveResult.NotFound
            else -> ResolveResult.Unsupported
        }
    }

    suspend fun findById(sourceId: SourceId, id: String): ResolveResult {
        val adapter = adaptersBySourceId[sourceId]
            ?: error("Unknown source id: $sourceId")
        val source = sources[sourceId.index]
        return when (val result = adapter.find(id)) {
            is FindResult.Found -> ResolveResult.Resolved(source, result.id)
            FindResult.NotFound -> ResolveResult.NotFound
            FindResult.Unsupported -> ResolveResult.Unsupported
        }
    }

    suspend fun loadMeta(video: ResolveResult.Resolved): LoadMetaResult {
        val adapter = adaptersBySourceId[video.source.id]
            ?: error("Unknown source: ${video.source.id}")
        return adapter.loadMeta(video.videoId)
    }

    fun enqueue(video: ResolveResult.Resolved, request: DownloadRequest): TaskId {
        val adapter = adaptersBySourceId[video.source.id]
            ?: error("Unknown source: ${video.source.id}")
        if (request.targetPath.parent == null) {
            error("targetPath must include a parent directory: ${request.targetPath}")
        }
        return dispatcher.submit(request.targetPath) { partPath, onProgress ->
            adapter.downloadVideo(video.videoId, request.quality, partPath, onProgress)
        }
    }

    fun cancel(taskId: TaskId) {
        dispatcher.cancel(taskId)
    }

    fun confirmOverwrite(taskId: TaskId, overwrite: Boolean) {
        dispatcher.confirmOverwrite(taskId, overwrite)
    }

    fun setConfig(config: AppConfig) {
        _config.value = config
    }

    private fun register(taskId: TaskId): Boolean {
        val registered = DownloadState(DownloadStatus.Queued)
        while (true) {
            val states = _downloads.value
            if (taskId in states) return false
            if (_downloads.compareAndSet(states, states + (taskId to registered))) return true
        }
    }

    private fun transition(taskId: TaskId, status: DownloadStatus) {
        _downloads.update { states ->
            val current = states[taskId]
            if (current?.status?.isTerminal == true) return@update states
            states + (taskId to DownloadState(status, current?.progress ?: Progress.Indeterminate))
        }
    }

    private fun updateProgress(taskId: TaskId, progress: Progress) {
        _downloads.update { states ->
            val current = states[taskId] ?: return@update states
            states + (taskId to current.copy(progress = progress))
        }
    }
}

private val DownloadStatus.isTerminal: Boolean
    get() = this is DownloadStatus.Completed || this is DownloadStatus.Cancelled || this is DownloadStatus.Failed
