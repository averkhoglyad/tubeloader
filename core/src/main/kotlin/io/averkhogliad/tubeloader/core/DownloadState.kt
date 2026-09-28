package io.averkhogliad.tubeloader.core

import java.nio.file.Path

sealed interface DownloadState {
    data object Queued : DownloadState

    data object LoadingMeta : DownloadState

    data class Downloading(val progress: Progress) : DownloadState

    data object Finalizing : DownloadState

    data object Completed : DownloadState

    data object Cancelled : DownloadState

    data class Failed(val error: Throwable) : DownloadState

    data class Interrupted(val pending: PendingInteraction) : DownloadState
}

sealed interface PendingInteraction {
    data class OverwriteConfirmation(val targetPath: Path) : PendingInteraction
}
