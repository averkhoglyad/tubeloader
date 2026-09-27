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
    private val mediaTool: MediaTool,
    initialConfig: AppConfig = AppConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
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

    private val dispatcher = DownloadDispatcher(scope, mediaTool, ::transition)

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
        return dispatcher.submit {
            when (request.quality.kind) {
                MediaKind.Video ->
                    adapter.downloadVideo(video.videoId, request.quality, request.targetPath)

                MediaKind.Audio ->
                    adapter.downloadAudio(video.videoId, request.quality, request.targetPath)
            }
        }
    }

    fun cancel(taskId: TaskId) {
        dispatcher.cancel(taskId)
    }

    fun setConfig(config: AppConfig) {
        _config.value = config
    }

    private fun transition(taskId: TaskId, state: DownloadState) {
        _downloads.update { it + (taskId to state) }
    }
}
