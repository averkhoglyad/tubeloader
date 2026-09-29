package io.averkhogliad.tubeloader.core

import java.nio.file.Path

interface MediaOperation {
    val inputs: List<Path>
    val output: Path
}

interface MediaTool {
    suspend fun initialize(): Result<Unit>

    /**
     * Cancelling the coroutine this call runs in interrupts the operation. The implementation must
     * then kill the whole process tree of the tool it started; orphaned children are not allowed.
     */
    suspend fun run(operation: MediaOperation, onProgress: (Progress) -> Unit): Result<Unit>
}
