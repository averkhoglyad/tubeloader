package io.averkhogliad.tubeloader.core

import java.nio.file.Path

class FakeSourceAdapter(
    override val displayName: String = "fake",
    override val capability: DownloadCapability = DownloadCapability.Delegate,
) : SourceAdapter {

    var onFind: suspend (String) -> FindResult = { FindResult.Unsupported }
    var onLoadMeta: suspend (String) -> LoadMetaResult = { LoadMetaResult.NotFound }
    var onDownload: suspend (DownloadRequest) -> Unit = {}

    val downloaded = mutableListOf<DownloadRequest>()

    override suspend fun find(input: String): FindResult = onFind(input)

    override suspend fun loadMeta(id: String): LoadMetaResult = onLoadMeta(id)

    override suspend fun downloadVideo(id: String, quality: Quality, targetPath: Path) =
        download(DownloadRequest(id, quality, targetPath))

    override suspend fun downloadAudio(id: String, quality: Quality, targetPath: Path) =
        download(DownloadRequest(id, quality, targetPath))

    private suspend fun download(request: DownloadRequest) {
        downloaded += request
        onDownload(request)
    }
}
