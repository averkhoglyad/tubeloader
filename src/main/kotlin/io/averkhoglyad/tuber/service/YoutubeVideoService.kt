package io.averkhoglyad.tuber.service

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
import java.nio.file.StandardOpenOption
import kotlin.coroutines.coroutineContext

private const val audioAndVideoDownloadingProgressPart = 0.95
private const val encodingProgressPart = 1 - audioAndVideoDownloadingProgressPart

class YoutubeVideoService(private val downloader: YoutubeDownloader) {

    private val logger by log4j()

    fun parseVideoId(url: String): String? {
        val query = url.takeIf { it.isNotBlank() }
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

    suspend fun videoInfo(videoId: String, progressCh: Channel<Double>? = null): VideoInfo? {
        val request = RequestVideoInfo(videoId)
        progressCh?.let { request.callback(callback("Loading video info for ID ${videoId}", progressCh)) }
        return downloader.getVideoInfo(request)?.data()
    }

    suspend fun downloadFromYoutube(target: Path,
                                    format: Format,
                                    progressCh: Channel<Double>? = null) {
        Files.newOutputStream(target).use { out ->
            val request = RequestVideoStreamDownload(format, out)
            progressCh?.let { request.callback(callback("${target.fileName} downloading progress", progressCh)) }
            downloader.downloadVideoStream(request)
            progressCh?.send(1.0)
            logger.debug("${target.fileName} downloading is done")
        }
    }

    private suspend fun <S> callback(logMessage: String, progressCh: Channel<Double>): YoutubeProgressCallback<S> {
        val ctx = coroutineContext
        return object : YoutubeProgressCallback<S> {
            override fun onFinished(file: S) {}
            override fun onError(throwable: Throwable) {}
            override fun onDownloading(progress: Int) {
                logger.debug("${logMessage}: ${progress}%")
                ctx.ensureActive()
                GlobalScope.launch(ctx) {
                    progressCh.takeUnless { it.isClosedForSend }
                        ?.send(progress / 100.0)
                }
            }
        }
    }

    suspend fun downloadVideoWithAudio(target: Path,
                                       videoFmt: VideoFormat,
                                       audioFmt: AudioFormat,
                                       progressCh: Channel<Double>? = null) {
        if (Files.exists(target)) {
            Files.write(target, byteArrayOf(), StandardOpenOption.TRUNCATE_EXISTING)
        } else { // To pin filename in the filesystem
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
                        progressCh?.send((progressAudio * audioPortion + progressVideo * videoPortion) * audioAndVideoDownloadingProgressPart)
                    }
                }
                launch {
                    videoCh.consumeEach {
                        progressVideo = it
                        progressCh?.send((progressAudio * audioPortion + progressVideo * videoPortion) * audioAndVideoDownloadingProgressPart)
                    }
                }

                val audioDef = async { downloadFromYoutube(tmpAudioFile, audioFmt, audioCh) }
                val videoDef = async { downloadFromYoutube(tmpVideoFile, videoFmt, videoCh) }
                awaitAll(audioDef, videoDef)

                launch {
                    encodeCh.consumeEach {
                        progressCh?.send(audioAndVideoDownloadingProgressPart + it * encodingProgressPart)
                    }
                }

                mergeVideoAndAudioFiles(target, tmpAudioFile, tmpVideoFile, encodeCh)
                progressCh?.send(1.0)
            } finally {
                quite { Files.delete(tmpAudioFile) }
                quite { Files.delete(tmpVideoFile) }
                quite { audioCh.close() }
                quite { videoCh.close() }
                quite { encodeCh.close() }
            }
        }
    }

    private suspend fun mergeVideoAndAudioFiles(target: Path, audioFile: Path, videoFile: Path, progressCh: Channel<Double>) {
        val multimediaObjects = listOf(MultimediaObject(audioFile.toFile()), MultimediaObject(videoFile.toFile()))
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
                logger.debug("${target.fileName} encoding progress: ${permil / 10.0}%")
                ctx.ensureActive()
                if (permil >= 0) {
                    GlobalScope.launch(ctx) {
                        progressCh.takeUnless { it.isClosedForSend }
                            ?.send(permil / 1000.0)
                    }
                }
            }

            override fun message(message: String?) {
                logger.warn("FFMPEG: $message")
            }
        }
        Encoder().encode(multimediaObjects, target.toFile(), encodingAttributes, listener)
        logger.debug("${target.fileName} encoding is done")
    }
}