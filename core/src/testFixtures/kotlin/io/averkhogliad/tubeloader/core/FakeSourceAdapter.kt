package io.averkhogliad.tubeloader.core

import java.nio.file.Path

class FakeSourceAdapter(
    override val capability: DownloadCapability = DownloadCapability.Delegate,
) : SourceAdapter {

    var onParseUrl: suspend (String) -> ParseUrlResult = { ParseUrlResult.Unsupported }
    var onLoadMeta: suspend (String) -> LoadMetaResult = { LoadMetaResult.NotFound }
    var onDownload: suspend (DownloadRequest) -> Unit = {}

    val downloaded = mutableListOf<DownloadRequest>()

    override suspend fun parseUrl(url: String): ParseUrlResult = onParseUrl(url)

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
