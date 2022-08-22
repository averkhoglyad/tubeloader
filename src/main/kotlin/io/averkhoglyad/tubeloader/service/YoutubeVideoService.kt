package io.averkhoglyad.tubeloader.service

import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.YoutubeProgressCallback
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload
import com.github.kiulian.downloader.model.Extension
import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.AudioFormat
import com.github.kiulian.downloader.model.videos.formats.Format
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import com.github.kiulian.downloader.model.videos.formats.VideoWithAudioFormat
import com.github.kiulian.downloader.model.videos.quality.VideoQuality
import io.averkhoglyad.tubeloader.data.DownloadOption
import io.averkhoglyad.tubeloader.data.VideoDetails
import io.averkhoglyad.tubeloader.util.log4j
import io.averkhoglyad.tubeloader.util.quietly
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import org.apache.logging.log4j.util.Supplier
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
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val audioAndVideoDownloadingProgressPart = 0.95
private const val encodingProgressPart = 1 - audioAndVideoDownloadingProgressPart

class YoutubeVideoService(private val downloader: YoutubeDownloader) {

    private val logger by log4j()

    // TODO: Refactoring is needed
    fun parseVideoId(url: String): String? {
        val query = url.takeIf { it.isNotBlank() }
            ?.let { it.trim() } ?: return null
        val url: URL = quietly { URL(query) } ?: return null
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

    suspend fun videoInfo(videoId: String, progressCh: Channel<Double>? = null): VideoDetails? {
        val request = RequestVideoInfo(videoId)
        progressCh?.let { request.callback(callback("Loading video info for ID ${videoId}", progressCh)) }
        val videoInfo = downloader.getVideoInfo(request)?.data()
        return videoInfo?.let { createVideoDetails(it) }
    }

    private fun createVideoDetails(info: VideoInfo): VideoDetails {
        return VideoDetails(
            id = info.details().videoId(),
            title = info.details().title(),
            author = info.details().author(),
            thumbnail = info.details().thumbnails().firstOrNull(),
            duration = info.details().lengthSeconds().toDuration(DurationUnit.SECONDS),
            downloadOptions = detectSupportedDownloadOptions(info)
        )
    }

    private fun detectSupportedDownloadOptions(video: VideoInfo): List<DownloadOption> {
        val bestAudioFormat = video.bestAudioFormat()
        val bestVideoWithAudioFormat = video.bestVideoWithAudioFormat()
        val videoWithAudioFormats = video.videoWithAudioFormats()

        logger.debug("Detecting supported formats for video: {}", video.details().videoId())
        logger.debug("-- detected best audio format {}", Supplier { bestAudioFormat.asString() })
        logger.debug("-- detected best video with audio format {}", Supplier { bestVideoWithAudioFormat.asString() })
        logger.debug("-- detected all video with audio formats: {}", Supplier { videoWithAudioFormats.asString() })

        val highQualityVideoWithoutAudioFormats = video.videoFormats()
            .filter { it.videoQuality().ordinal > bestVideoWithAudioFormat.videoQuality().ordinal }

        logger.debug("-- detected high quality without audio formats: {}", Supplier { highQualityVideoWithoutAudioFormats.asString() })

        val allFormats = highQualityVideoWithoutAudioFormats + videoWithAudioFormats

        logger.debug("-- all selected video formats: {}", Supplier { allFormats.asString() })

        val options = allFormats
            .filter { it.videoQuality() > VideoQuality.noVideo }
            .sortedByDescending { it.videoQuality().ordinal }
            .groupBy { it.videoQuality() }
            .flatMap { (_, formats) ->
                formats.groupBy { it.fps() }.values
            }
            .map { formats ->
                val videoFmt = formats.sortedByDescending { it.bitrate() }.first()
                createDownloadOption(videoFmt, bestAudioFormat)
            }

        logger.debug("-- prepared download options: {}", Supplier { options.asString() })

        return options
    }

    private fun createDownloadOption(videoFmt: VideoFormat, audioFmt: AudioFormat): DownloadOption {
        val extension = videoFmt.extension().takeUnless { it == Extension.WEBM }?.value() ?: "mp4"
        val label = videoFmt.qualityLabel()
        return DownloadOption(label, extension, FormatToDownload(videoFmt, audioFmt))
    }

    suspend fun downloadFromYoutube(target: Path, option: DownloadOption, progressCh: Channel<Double>? = null) {
        val (videoFmt, audioFmt) = option.data as FormatToDownload
        if (videoFmt is VideoWithAudioFormat) {
            return doDownloadFromYoutube(target, videoFmt, progressCh)
        } else {
            return doDownloadVideoWithAudio(target, videoFmt, audioFmt, progressCh)
        }
    }

    private suspend fun doDownloadFromYoutube(target: Path, format: Format, progressCh: Channel<Double>? = null) {
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

    private  suspend fun doDownloadVideoWithAudio(target: Path,
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

                val audioDef = async { doDownloadFromYoutube(tmpAudioFile, audioFmt, audioCh) }
                val videoDef = async { doDownloadFromYoutube(tmpVideoFile, videoFmt, videoCh) }
                awaitAll(audioDef, videoDef)

                launch {
                    encodeCh.consumeEach {
                        progressCh?.send(audioAndVideoDownloadingProgressPart + it * encodingProgressPart)
                    }
                }

                mergeVideoAndAudioFiles(target, tmpAudioFile, tmpVideoFile, encodeCh)
                progressCh?.send(1.0)
            } finally {
                quietly { Files.delete(tmpAudioFile) }
                quietly { Files.delete(tmpVideoFile) }
                quietly { audioCh.close() }
                quietly { videoCh.close() }
                quietly { encodeCh.close() }
            }
        }
    }

    private suspend fun mergeVideoAndAudioFiles(target: Path,
                                                audioFile: Path,
                                                videoFile: Path,
                                                progressCh: Channel<Double>) {
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

private fun AudioFormat.asString() = "${audioQuality()} ${extension().value()} ${mimeType()} ${averageBitrate()}bps"

private fun VideoFormat.asString() = "${qualityLabel()} ${extension().value()} ${mimeType()} ${fps()}fps"

private fun List<Any>.asString() = this.takeUnless { it.isEmpty() }
    ?.let { items ->
        items.asSequence()
            .map {
                when (it) {
                    is AudioFormat -> it.asString()
                    is VideoFormat -> it.asString()
                    else -> it.toString()
                }
            }
            .joinToString("\n  - ", prefix = "\n  - ")
    } ?: "<empty>"

private data class FormatToDownload(val video: VideoFormat, val audio: AudioFormat)