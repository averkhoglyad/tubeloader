package io.averkhoglyad.tuber

import com.github.kiulian.downloader.Config
import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.YoutubeProgressCallback
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import javafx.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tornadofx.launch
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths

fun main(args: Array<String>) {
    launch<TuberApp>(args)
}

fun main0() {
    val downloader = YoutubeDownloader()
    val config: Config = downloader.getConfig()
    config.setMaxRetries(0)

    // https://www.youtube.com/watch?v=zmcCy_UfrOc

    val request = RequestVideoInfo("zmcCy_UfrOc")
    val response = downloader.getVideoInfo(request)
    val video = response.data()

//    val subResp = downloader.downloadSubtitle(RequestSubtitlesDownload(video.subtitlesInfo().first()))
//    val subtitlesString: String = subResp.data()
//    println(subtitlesString)

    val format: VideoFormat = video.bestVideoWithAudioFormat()

    val outFilePath = Paths.get("out", "video.${format.extension().value()}")
    Files.newOutputStream(outFilePath).use { out ->
        runBlocking {
            try {
                val download = downloader.download(format, out)
                download
                    .consumeEach {
                        println("Progress: $it%")
                    }
            } catch (e: Exception) {
                println("Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

fun YoutubeDownloader.download(format: VideoFormat, out: OutputStream): Channel<Int> {
    val ch = Channel<Int>()
    val req = RequestVideoStreamDownload(format, out)
        .callback(object : YoutubeProgressCallback<Void> {
            override fun onFinished(file: Void?) {
                ch.close()
            }
            override fun onError(throwable: Throwable) {
                ch.close(throwable)
            }
            override fun onDownloading(progress: Int) {
                GlobalScope.launch { ch.send(progress) }
            }
        })
    GlobalScope.launch(Dispatchers.IO) {
        downloadVideoStream(req)
    }
    return ch
}