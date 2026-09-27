package io.averkhogliad.tubeloader.core

import java.nio.file.Path

enum class DownloadCapability { Delegate, Native, ResolveOnly }

sealed interface ParseUrlResult {
    data class Supported(val id: String) : ParseUrlResult

    data object Unsupported : ParseUrlResult

    data object NotFound : ParseUrlResult
}

sealed interface LoadMetaResult {
    data class Found(val meta: VideoMeta) : LoadMetaResult

    data object NotFound : LoadMetaResult
}

interface SourceAdapter {
    val capability: DownloadCapability

    suspend fun parseUrl(url: String): ParseUrlResult

    suspend fun loadMeta(id: String): LoadMetaResult

    suspend fun downloadVideo(id: String, quality: Quality, targetPath: Path)

    suspend fun downloadAudio(id: String, quality: Quality, targetPath: Path)
}
