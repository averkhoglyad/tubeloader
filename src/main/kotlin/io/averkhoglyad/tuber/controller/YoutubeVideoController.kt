package io.averkhoglyad.tuber.controller

import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.YoutubeProgressCallback
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload
import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.AudioFormat
import com.github.kiulian.downloader.model.videos.formats.Format
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import io.averkhoglyad.tuber.util.log4j
import io.averkhoglyad.tuber.util.quite
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
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

    fun loadVideoInfo(videoId: String): Deferred<VideoInfo?> {
        val request = RequestVideoInfo(videoId)
        return GlobalScope.async(Dispatchers.IO) {
            downloader.getVideoInfo(request)?.data()
        }
    }

    fun downloadVideo(target: Path, videoFormat: VideoFormat, audioFormat: AudioFormat?): Channel<Double> {
        if (audioFormat == null) {
            return downloadFromYoutube(target, videoFormat)
        } else {
            return downloadVideoWithAudio(target, videoFormat, audioFormat)
        }
    }

    private fun downloadVideoWithAudio(target: Path, videoFmt: VideoFormat, audioFmt: AudioFormat): Channel<Double> {
        Files.createFile(target) // To pin filename in the filesystem

        val tmpAudioFile = Files.createTempFile(target.parent, "${target.fileName}.", ".a.tmp")
        val tmpVideoFile = Files.createTempFile(target.parent, "${target.fileName}.", ".v.tmp")

        val ch = Channel<Double>()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                fun concatAudioAndVideoAndFinish() {
                    val progress = concatVideoAndAudioFiles(target, tmpAudioFile, tmpVideoFile)
                    GlobalScope.launch {
                        progress.consumeEach {
                            if (!ch.isClosedForSend) { // Could be already closed because async execution
                                ch.send(0.9 + it * 0.1)
                            }
                        }
                    }
                    progress.invokeOnClose {
                        quite { Files.delete(tmpAudioFile) }
                        quite { Files.delete(tmpVideoFile) }
                        GlobalScope.launch {
                            ch.send(1.0)
                            ch.close(it)
                        }
                    }
                }

                var progressAudio = 0.0
                var progressVideo = 0.0
                val audioCh = downloadFromYoutube(tmpAudioFile, audioFmt)
                val videoCh = downloadFromYoutube(tmpVideoFile, videoFmt)

                audioCh.consumeEach {
                    progressAudio = it
                    ch.send((progressAudio + progressVideo) * 0.45)
                }
                audioCh.invokeOnClose {
                    GlobalScope.launch {
                        if (it != null) {
                            ch.close(it)
                            videoCh.cancel()
                        } else {
                            progressAudio = 1.0
                            ch.send((progressAudio + progressVideo) * 0.45)
                        }
                        if (videoCh.isClosedForSend) {
                            concatAudioAndVideoAndFinish()
                        }
                    }
                }

                videoCh.consumeEach {
                    progressVideo = it
                    ch.send((progressAudio + progressVideo) * 0.45)
                }
                videoCh.invokeOnClose {
                    GlobalScope.launch {
                        if (it != null) {
                            ch.close(it)
                            audioCh.cancel()
                        } else {
                            progressVideo = 1.0
                            ch.send((progressAudio + progressVideo) * 0.45)
                            if (audioCh.isClosedForSend) {
                                concatAudioAndVideoAndFinish()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error on file download", e)
                ch.close(e)
            }
        }

        return ch
    }

    private fun concatVideoAndAudioFiles(target: Path, tmpAudioFile: Path, tmpVideoFile: Path): Channel<Double> {
        val multimediaObjects = listOf(MultimediaObject(tmpAudioFile.toFile()), MultimediaObject(tmpVideoFile.toFile()))
        val encodingAttributes = EncodingAttributes().apply {
            setAudioAttributes(AudioAttributes().apply {
                setCodec(AudioAttributes.DIRECT_STREAM_COPY)
            })
            setVideoAttributes(VideoAttributes().apply {
                setCodec(VideoAttributes.DIRECT_STREAM_COPY)
            })
        }

        val ch = Channel<Double>()
        val listener = object : EncoderProgressListener {
            override fun sourceInfo(info: MultimediaInfo?) {
                info?.let { logger.info("FFMPEG: $info") }
            }

            override fun progress(permil: Int) {
                GlobalScope.launch {
                    ch.send(permil / 1000.0)
                }
            }

            override fun message(message: String?) {
                logger.warn("FFMPEG: $message")
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                Encoder().encode(multimediaObjects, target.toFile(), encodingAttributes, listener)
                ch.close()
            } catch (e: Exception) {
                ch.close(e)
            }
        }
        return ch
    }

    private fun downloadFromYoutube(target: Path, format: Format): Channel<Double> {
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
                            GlobalScope.launch {
                                if(!ch.isClosedForSend) {
                                    ch.send(progress / 100.0)
                                }
                            }
                        }
                    })
                downloader.downloadVideoStream(req)
            }
        }
        return ch
    }
}
