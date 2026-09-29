package io.averkhogliad.tubeloader.core

class FakeMediaTool : MediaTool {

    var onInitialize: suspend () -> Result<Unit> = { Result.success(Unit) }
    var onRun: suspend (MediaOperation) -> Result<Unit> = { Result.success(Unit) }

    var initializeCalls = 0
        private set

    override suspend fun initialize(): Result<Unit> {
        initializeCalls++
        return onInitialize()
    }

    override suspend fun run(operation: MediaOperation, onProgress: (Progress) -> Unit): Result<Unit> =
        onRun(operation)
}
