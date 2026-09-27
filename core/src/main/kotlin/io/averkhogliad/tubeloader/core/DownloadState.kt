package io.averkhogliad.tubeloader.core

sealed interface DownloadState {
    data object Queued : DownloadState

    data object LoadingMeta : DownloadState

    data class Downloading(val progress: Progress) : DownloadState

    data object Finalizing : DownloadState

    data object Completed : DownloadState

    data object Cancelled : DownloadState

    data class Failed(val error: Throwable) : DownloadState
}
