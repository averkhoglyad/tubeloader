package io.averkhoglyad.tuber.controller

import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.YoutubeProgressCallback
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload
import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.AudioFormat
import com.github.kiulian.downloader.model.videos.formats.Format
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import com.github.kiulian.downloader.model.videos.formats.VideoWithAudioFormat
import io.averkhoglyad.tuber.data.DownloadTask
import io.averkhoglyad.tuber.data.TaskStatus
import io.averkhoglyad.tuber.util.log4j
import io.averkhoglyad.tuber.util.quite
import javafx.beans.property.ReadOnlyDoubleWrapper
import javafx.beans.property.ReadOnlyObjectWrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.javafx.JavaFx
import tornadofx.Controller
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import ws.schild.jave.info.MultimediaInfo
import ws.schild.jave.progress.EncoderProgressListener
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

// TODO: Logic must be encapsulated in separated service and injected to controller
class YoutubeVideoController : Controller() {

    private val logger by log4j()

    private val downloader = YoutubeDownloader()

    fun parseVideoIdFromUrl(str: String): String? {
        val query = str.takeIf { it.isNotBlank() }
            ?.let { it.trim() } ?: return null
        val url: URL = quite { URL(query) } ?: return null
        val host = url.host.replace("^www\\.".toRegex(), "")
        if (host.startsWith("youtube")) {
            if (url.path.startsWith("/shorts/")) {
                return url.path.substring("/shorts/".length)
            }
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

    fun loadVideoInfoAsync(videoId: String): Deferred<VideoInfo?> {
        val request = RequestVideoInfo(videoId)
        return GlobalScope.async(Dispatchers.IO) {
            downloader.getVideoInfo(request)?.data()
        }
    }

    fun downloadVideo(target: Path, video: VideoInfo, format: VideoFormat): DownloadTask {
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
            doDownloadFromYoutube(target, format, ch)
        }
    }

    private fun downloadVideoWithAudio(target: Path, video: VideoInfo, videoFmt: VideoFormat, audioFmt: AudioFormat): DownloadTask {
        return executeDownloadTask(target, video) { ch ->
            doDownloadVideoWithAudio(target, videoFmt, audioFmt, ch)
        }
    }

    private fun executeDownloadTask(target: Path, video: VideoInfo, block: suspend (Channel<Double>) -> Unit): DownloadTask {
        val ch = Channel<Double>()
        val status = ReadOnlyObjectWrapper(TaskStatus.IN_PROGRESS)
        val progress = ReadOnlyDoubleWrapper()
        GlobalScope.launch(Dispatchers.JavaFx) {
            ch.consumeEach {
                progress.set(it)
            }
        }
        val job = GlobalScope.launch(Dispatchers.IO) {
            try {
                block(ch)
                GlobalScope.launch(Dispatchers.JavaFx) { status.set(TaskStatus.DONE) }
            } catch (e: CancellationException) {
                logger.debug("Download is canceled")
                GlobalScope.launch(Dispatchers.JavaFx) { status.set(TaskStatus.CANCELED) }
            } catch (e: Exception) {
                logger.error("Error on downloading")
                GlobalScope.launch(Dispatchers.JavaFx) { status.set(TaskStatus.FAILED) }
                throw e // TODO:::
            } finally {
                ch.close()
            }
        }
        return DownloadTask(video, target, progress.readOnlyProperty, status.readOnlyProperty) {
            logger.debug("Request to cancel download")
            job.cancel()
            GlobalScope.launch(Dispatchers.JavaFx) { status.set(TaskStatus.CANCELING) }
        }
    }

    private suspend fun doDownloadFromYoutube(target: Path, format: Format, progressCh: Channel<Double>) {
        val ctx = coroutineContext
        Files.newOutputStream(target).use { out ->
            val req = RequestVideoStreamDownload(format, out)
                .callback(object : YoutubeProgressCallback<Void> {
                    override fun onFinished(file: Void?) {}
                    override fun onError(throwable: Throwable) {}
                    override fun onDownloading(progress: Int) {
                        ctx.ensureActive()
                        GlobalScope.launch(ctx) {
                            progressCh.takeUnless { it.isClosedForSend }
                                ?.send(progress / 100.0)
                        }
                    }
                })
            downloader.downloadVideoStream(req)
            progressCh.send(1.0)
        }
    }

    private suspend fun doDownloadVideoWithAudio(target: Path,
                                                 videoFmt: VideoFormat,
                                                 audioFmt: AudioFormat,
                                                 progressCh: Channel<Double>) {
        if (!Files.exists(target)) { // To pin filename in the filesystem
            Files.createFile(target)
        }
        val tmpAudioFile = Files.createTempFile(target.parent, "${target.fileName}.", ".a.tmp")
        val tmpVideoFile = Files.createTempFile(target.parent, "${target.fileName}.", ".v.tmp")

        coroutineScope {
            val audioCh = Channel<Double>()
            val videoCh = Channel<Double>()
            val encodeCh = Channel<Double>()
            try {
                var progressAudio = 0.0
                var progressVideo = 0.0

                val audioPortion =
                    audioFmt.contentLength().toDouble() / (audioFmt.contentLength() + videoFmt.contentLength())
                val videoPortion =
                    videoFmt.contentLength().toDouble() / (audioFmt.contentLength() + videoFmt.contentLength())

                launch {
                    audioCh.consumeEach {
                        progressAudio = it
                        progressCh.send((progressAudio * audioPortion + progressVideo * videoPortion) * 0.95)
                    }
                }
                launch {
                    videoCh.consumeEach {
                        progressVideo = it
                        progressCh.send((progressAudio * audioPortion + progressVideo * videoPortion) * 0.95)
                    }
                }

                val audioDef = async { doDownloadFromYoutube(tmpAudioFile, audioFmt, audioCh) }
                val videoDef = async { doDownloadFromYoutube(tmpVideoFile, videoFmt, videoCh) }
                awaitAll(audioDef, videoDef)

                launch {
                    encodeCh.consumeEach {
                        progressCh.send(0.95 + it * 0.1)
                    }
                }

                concatVideoAndAudioFiles(target, tmpAudioFile, tmpVideoFile, encodeCh)
                progressCh.send(1.0)
            } finally {
                quite { Files.delete(tmpAudioFile) }
                quite { Files.delete(tmpVideoFile) }
                audioCh.close()
                videoCh.close()
                encodeCh.close()
            }
        }
    }

    private suspend fun concatVideoAndAudioFiles(target: Path, tmpAudioFile: Path, tmpVideoFile: Path, progressCh: Channel<Double>) {
        val multimediaObjects = listOf(MultimediaObject(tmpAudioFile.toFile()), MultimediaObject(tmpVideoFile.toFile()))
        val encodingAttributes = EncodingAttributes().apply {
            setAudioAttributes(AudioAttributes().apply {
                setCodec(AudioAttributes.DIRECT_STREAM_COPY)
            })
            setVideoAttributes(VideoAttributes().apply {
                setCodec(VideoAttributes.DIRECT_STREAM_COPY)
            })
        }
        val ctx = coroutineContext
        val listener = object : EncoderProgressListener {
            override fun sourceInfo(info: MultimediaInfo?) {
                info?.let { logger.info("FFMPEG: $info") }
            }

            override fun progress(permil: Int) {
                ctx.ensureActive()
                GlobalScope.launch(ctx) {
                    progressCh.takeUnless { it.isClosedForSend }
                        ?.send(permil / 1000.0)
                }
            }

            override fun message(message: String?) {
                logger.warn("FFMPEG: $message")
            }
        }
        Encoder()
            .encode(multimediaObjects, target.toFile(), encodingAttributes, listener)
    }
}
