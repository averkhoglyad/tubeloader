package io.averkhogliad.tubeloader.core

import java.nio.file.Path

interface MediaOperation {
    val inputs: List<Path>
    val output: Path
}

interface MediaTool {
    suspend fun initialize(): Result<Unit>

    suspend fun run(operation: MediaOperation, onProgress: (Progress) -> Unit): Result<Unit>
}
