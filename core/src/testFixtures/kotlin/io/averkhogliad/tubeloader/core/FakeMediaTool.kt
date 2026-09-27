package io.averkhogliad.tubeloader.core

class FakeMediaTool : MediaTool {

    var onInitialize: suspend () -> Unit = {}
    var onRun: suspend (MediaOperation) -> MediaToolResult = { MediaToolResult.Success }

    var initializeCalls = 0
        private set

    override suspend fun initialize() {
        initializeCalls++
        onInitialize()
    }

    override suspend fun run(operation: MediaOperation, onProgress: (Progress) -> Unit): MediaToolResult =
        onRun(operation)
}
