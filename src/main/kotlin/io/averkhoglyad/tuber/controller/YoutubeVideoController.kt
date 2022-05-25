package io.averkhoglyad.tuber.controller

import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.YoutubeProgressCallback
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload
import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import io.averkhoglyad.tuber.util.log4j
import io.averkhoglyad.tuber.util.quite
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.javafx.JavaFx
import tornadofx.Controller
import java.io.OutputStream
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class YoutubeVideoController : Controller() {

    private val logger by log4j()

    // TODO: Must be encapsulated in separated service and injected to controller
    private val downloader = YoutubeDownloader()

    fun parseVideoIdFromUrl(str: String): String? {
        val query = str.takeIf { it.isNotBlank() }
            ?.let { it.trim() } ?: return null
        val url: URL = quite { URL(query) } ?: return null
        val host = url.host.replace("^www\\.".toRegex(), "")
        if (host.startsWith("youtube")) {
            return (url.query ?: "").split("&")
                .map { it.split("=", limit = 2) }
                .firstOrNull { it[0] == "v" }
                ?.let { URLDecoder.decode(it[1], StandardCharsets.UTF_8) }
        }
        if (host == "youtu.be") {
            return url.path.substring(1)
        }
        return null
    }

    fun loadVideoInfo(videoId: String): Deferred<VideoInfo?> {
        val request = RequestVideoInfo(videoId)
        return GlobalScope.async(Dispatchers.IO) {
            downloader.getVideoInfo(request)?.data()
        }
    }

    fun downloadVideo(format: VideoFormat, target: Path): Channel<Double> {
        val ch = Channel<Double>()
        GlobalScope.launch(Dispatchers.IO) {
            Files.newOutputStream(target).use { out ->
                val req = RequestVideoStreamDownload(format, out)
                    .callback(object : YoutubeProgressCallback<Void> {
                        override fun onFinished(file: Void?) {
                            ch.close()
                        }
                        override fun onError(throwable: Throwable) {
                            ch.close(throwable)
                        }
                        override fun onDownloading(progress: Int) {
                            GlobalScope.launch { ch.send(progress / 100.0) }
                        }
                    })
                downloader.downloadVideoStream(req)
            }
        }
        return ch
    }
}
