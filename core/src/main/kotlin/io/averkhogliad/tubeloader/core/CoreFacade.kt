package io.averkhogliad.tubeloader.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class CoreFacade(
    private val adapters: List<SourceAdapter>,
    private val mediaTool: MediaTool,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _downloads = MutableStateFlow<Map<TaskId, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<TaskId, DownloadState>> = _downloads.asStateFlow()

    private val jobs = mutableMapOf<TaskId, Job>()
    private val adapterByVideoId = mutableMapOf<String, SourceAdapter>()
    private val toolLock = Mutex()
    private var toolReady = false

    suspend fun parseUrl(url: String): ParseUrlResult {
        var notFound = false
        for (adapter in adapters) {
            when (val result = adapter.parseUrl(url)) {
                is ParseUrlResult.Supported -> {
                    adapterByVideoId[result.id] = adapter
                    return result
                }

                ParseUrlResult.NotFound -> notFound = true
                ParseUrlResult.Unsupported -> Unit
            }
        }
        return if (notFound) ParseUrlResult.NotFound else ParseUrlResult.Unsupported
    }

    suspend fun loadMeta(id: String): LoadMetaResult {
        for (adapter in adapters) {
            val result = adapter.loadMeta(id)
            if (result is LoadMetaResult.Found) {
                adapterByVideoId[id] = adapter
                return result
            }
        }
        return LoadMetaResult.NotFound
    }

    fun enqueue(request: DownloadRequest): TaskId {
        val adapter = adapterByVideoId[request.id]
            ?: error("No adapter resolved for video ${request.id}")
        val taskId = TaskId(UUID.randomUUID().toString())
        transition(taskId, DownloadState.Queued)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                ensureToolReady()
                transition(taskId, DownloadState.Downloading(Progress.Indeterminate))
                when (request.quality.kind) {
                    MediaKind.Video ->
                        adapter.downloadVideo(request.id, request.quality, request.targetPath)

                    MediaKind.Audio ->
                        adapter.downloadAudio(request.id, request.quality, request.targetPath)
                }
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
        job.start()
        return taskId
    }

    fun cancel(taskId: TaskId) {
        jobs[taskId]?.cancel()
    }

    private fun transition(taskId: TaskId, state: DownloadState) {
        _downloads.update { it + (taskId to state) }
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
