package io.averkhogliad.tubeloader.core

sealed interface DownloadResult {
    data object Success : DownloadResult

    data class Failed(val error: DownloadError) : DownloadResult
}
