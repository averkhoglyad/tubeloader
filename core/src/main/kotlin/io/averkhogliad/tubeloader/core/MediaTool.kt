package io.averkhogliad.tubeloader.core

import java.nio.file.Path

interface MediaOperation {
    val inputs: List<Path>
    val output: Path
}

sealed interface MediaToolResult {
    data object Success : MediaToolResult

    data class Failure(val message: String) : MediaToolResult
}

interface MediaTool {
    suspend fun initialize()

    suspend fun run(operation: MediaOperation, onProgress: (Progress) -> Unit): MediaToolResult
}
