package io.averkhogliad.tubeloader.core

import java.nio.file.Path

data class DownloadState(
    val status: DownloadStatus,
    val progress: Progress = Progress.Indeterminate,
)

sealed interface DownloadStatus {
    data object Queued : DownloadStatus

    data object LoadingMeta : DownloadStatus

    data object Downloading : DownloadStatus

    data object Finalizing : DownloadStatus

    data object Completed : DownloadStatus

    data object Cancelled : DownloadStatus

    data class Failed(val error: Throwable) : DownloadStatus

    data class Interrupted(val pending: PendingInteraction) : DownloadStatus
}

sealed interface PendingInteraction {
    data class OverwriteConfirmation(val targetPath: Path) : PendingInteraction
}
