package io.averkhoglyad.tubeloader.controller

import io.averkhoglyad.tubeloader.data.DownloadOption
import io.averkhoglyad.tubeloader.data.DownloadTask
import io.averkhoglyad.tubeloader.data.TaskStatus
import io.averkhoglyad.tubeloader.data.VideoDetails
import io.averkhoglyad.tubeloader.service.YoutubeVideoService
import io.averkhoglyad.tubeloader.util.log4j
import io.averkhoglyad.tubeloader.util.quietly
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

    fun loadVideoInfoAsync(videoId: String): Deferred<VideoDetails?> {
        return GlobalScope.async(Dispatchers.IO) {
            service.videoInfo(videoId)
        }
    }

    fun downloadVideoAsync(target: Path, video: VideoDetails, op: DownloadOption): DownloadTask {
        return executeDownloadTask(target, video) { ch ->
            service.downloadFromYoutube(target, op, ch)
        }
    }

    private fun executeDownloadTask(target: Path,
                                    video: VideoDetails,
                                    block: suspend (Channel<Double>) -> Unit): DownloadTask {
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
                quietly { Files.delete(target) }
                GlobalScope.launch(Dispatchers.JavaFx) { task.status(TaskStatus.CANCELED) }
            } catch (e: Exception) {
                logger.error("Error on downloading", e)
                quietly { Files.delete(target) }
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
