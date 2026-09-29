package io.averkhogliad.tubeloader.core

import java.nio.file.Path

data class MuxCall(val video: Path, val audio: Path, val output: Path)

data class RemuxCall(val input: Path, val output: Path)

class FakeMediaTool : MediaTool {

    var onMux: suspend (Path, Path, Path) -> Result<Unit> = { _, _, _ -> Result.success(Unit) }
    var onRemux: suspend (Path, Path) -> Result<Unit> = { _, _ -> Result.success(Unit) }

    var progress: Progress? = null

    val muxCalls = mutableListOf<MuxCall>()
    val remuxCalls = mutableListOf<RemuxCall>()

    override suspend fun mux(
        video: Path,
        audio: Path,
        output: Path,
        onProgress: (Progress) -> Unit,
    ): Result<Unit> {
        muxCalls += MuxCall(video, audio, output)
        progress?.let(onProgress)
        return onMux(video, audio, output)
    }

    override suspend fun remux(
        input: Path,
        output: Path,
        onProgress: (Progress) -> Unit,
    ): Result<Unit> {
        remuxCalls += RemuxCall(input, output)
        progress?.let(onProgress)
        return onRemux(input, output)
    }
}
