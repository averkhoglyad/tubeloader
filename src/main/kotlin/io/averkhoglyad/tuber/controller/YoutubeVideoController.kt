package io.averkhoglyad.tuber.controller

import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.AudioFormat
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import com.github.kiulian.downloader.model.videos.formats.VideoWithAudioFormat
import io.averkhoglyad.tuber.data.DownloadTask
import io.averkhoglyad.tuber.data.TaskStatus
import io.averkhoglyad.tuber.service.YoutubeVideoService
import io.averkhoglyad.tuber.util.log4j
import io.averkhoglyad.tuber.util.quite
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.javafx.JavaFx
import tornadofx.Controller
import java.nio.file.Files
import java.nio.file.Path

class YoutubeVideoController : Controller() {

    private val logger by log4j()

    private val service by di<YoutubeVideoService>()

    fun parseVideoIdFromUrl(str: String): String? {
        return service.parseVideoId(str)
    }

    fun loadVideoInfoAsync(videoId: String): Deferred<VideoInfo?> {
        return GlobalScope.async(Dispatchers.IO) {
            service.videoInfo(videoId)
        }
    }

    fun downloadVideoAsync(target: Path, video: VideoInfo, format: VideoFormat): DownloadTask {
        if (format is VideoWithAudioFormat) {
            return downloadFromYoutube(target, video, format)
        }
        val audioFormat = video.bestAudioFormat()
        if (audioFormat == null) {
            return downloadFromYoutube(target, video, format)
        } else {
            return downloadVideoWithAudio(target, video, format, audioFormat)
        }
    }

    private fun downloadFromYoutube(target: Path, video: VideoInfo, format: VideoFormat): DownloadTask {
        return executeDownloadTask(target, video) { ch ->
            service.downloadFromYoutube(target, format, ch)
        }
    }

    private fun downloadVideoWithAudio(target: Path, video: VideoInfo, videoFmt: VideoFormat, audioFmt: AudioFormat): DownloadTask {
        return executeDownloadTask(target, video) { ch ->
            service.downloadVideoWithAudio(target, videoFmt, audioFmt, ch)
        }
    }

    private fun executeDownloadTask(target: Path, video: VideoInfo, block: suspend (Channel<Double>) -> Unit): DownloadTask {
        lateinit var task: DownloadTask
        val job = GlobalScope.launch(Dispatchers.IO) {
            val progressCh = Channel<Double>()
            GlobalScope.launch(Dispatchers.JavaFx) { progressCh.consumeEach { task.progress(it) } }
            try {
                block(progressCh)
                logger.debug("Download is done")
                GlobalScope.launch(Dispatchers.JavaFx) { task.status(TaskStatus.DONE) }
            } catch (e: CancellationException) {
                logger.debug("Download is canceled")
                quite { Files.delete(target) }
                GlobalScope.launch(Dispatchers.JavaFx) { task.status(TaskStatus.CANCELED) }
            } catch (e: Exception) {
                logger.error("Error on downloading", e)
                quite { Files.delete(target) }
                val message = "Error: ${e.message ?: "Error on downloading"}"
                GlobalScope.launch(Dispatchers.JavaFx) { task.status(TaskStatus.FAILED, message) }
            } finally {
                progressCh.close()
            }
        }
        task = DownloadTask(video, target) {
            logger.debug("Request to cancel download")
            job.cancel()
        }
        return task
    }
}
