package io.averkhoglyad.tubeloader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val profileDataUrl: String = "https://rutube.ru/api/profile/user/27927605/?client=wdp"
private const val playlistDataUrl: String = "https://rutube.ru/api/playlist/custom/400137/?client=wdp"
private const val playlistVideosUrl: String = "https://rutube.ru/api/playlist/custom/400137/videos/?page=1&client=wdp"
private const val params = "no_404=true&referer=https%253A%252F%252Frutube.ru&pver=v2"
//private const val params = "no_404=true&referer=https%253A%252F%252Frutube.ru&pver=v2&utm_source=embed&utm_medium=referral&utm_campaign=logo&utm_content=208439458494158a7a7610027dd268a9&utm_term=balun.courses&t=1&p=D4CffkYWvepEhRo-99A-Yw"

fun main() {
//    val targetDimension = "1920x\\d+"
//    val targetDimension = "1280x\\d+"
//    val targetDimension = "640x\\d+"
//    val targetDimension = "\\d+x2160"
    val targetDimension = "\\d+x1080"

    val tasks = mapOf(
        "b9852a3ffc38640bdf480f5c9d4d912f" to "Почему хороший продукт больше не преимущество - конкуренция, копирование и экономика IT-бизнеса",
        "c09266c09667ffe107dbd05fe36da004" to "Как AI изменит разработку ПО - будущее программистов, команд и IT-компаний",
        "ccc975b0b480ddcc6b3d168cf206dace" to "Performance Review в IT - как правильно оценивать разработчиков и сотрудников",
        "9b804e640f06944322c892168b7b4395" to "Лайвкодинг с Claude Code - Spec-Driven Development на реальном TypeScript-проекте",
        "b31c53262e22efadb94b471271066ca2" to "Мой воркфлоу агентного программирования - параллельные сессии и пулреквесты в опенсорс",
        "0cc3caed052ecc0f0fb6f78d54121db3" to "Джедайские техники Максима Дорофеева - продуктивность, созвоны, выгорание и главный миф о времени",
        "217354e8d19cf1ac769d5b7bfe14b26b" to "Как экономика в 2026 меняет требования к разработчикам на рынке IT",
    )

    tasks
        .forEach { (id, name) ->
            println("Loading: $name ($id)")
            doLoad("https://rutube.ru/video/$id/", Path("c:\\tmp\\rutube\\$name.mp4"), targetDimension)
            println("--------------------")
        }
}

fun doLoad(videoUrl: String, outputFile: Path, targetDimension: String = "1920x\\d+") {
    outputFile.parent.createDirectories()
    if (!Files.exists(outputFile)) {
        Files.createFile(outputFile)
    }

    val videoDataUrl = videoUrl.replace("https://rutube.ru/video/", "https://rutube.ru/api/play/options/")
        .let {
            if (it.lastIndexOf('?') < 0) "$it?$params"
            else "$it&$params"
        }

    println("Video Data URL: $videoDataUrl")

    val json = Json { ignoreUnknownKeys = true }

    val videoData: VideoData = json.decodeFromString(URL(videoDataUrl)
        .readText(Charsets.UTF_8))

    println("Title: ${videoData.title}")

    val m3u8 = videoData.videoBalancer.m3u8 ?: videoData.videoBalancer.default

    println("m3u8 URL: $m3u8")

    val playlistFileUrls: List<String> = URL(m3u8)
        .useLines { lines ->
            lines.filterNot { it.startsWith('#') }
                .toList()
        }

    println("Playlist URLs:")
    playlistFileUrls.forEach { println("\t $it") }
    println()

    val playlistFileUrl = playlistFileUrls
//        .last()
        .first { it.contains("\\?i=$targetDimension\\w*$".toRegex()) }

//        .filterNot { it.contains("\\?i=1920x\\d+\\w*$".toRegex()) }
//        .last()

//        .first { it.contains("\\?i=1152x720\\w*$".toRegex()) }
//        .first { it.contains("\\?i=1280x\\d+\\w*$".toRegex()) }
//        .first { it.contains("\\?i=1920x\\d+\\w*$".toRegex()) }
//        .first { it.contains("\\?i=2560x1440\\w*$".toRegex()) }

//    val playlistFileUrl = "https://river-1.rutube.ru/hls-vod/KxoET9CX31jPXquKWvn-HA/1786528242/3570/0x5000c500fa77c089/2320f249337d41af8bce8d71fbee34ed.mp4.m3u8?i=3840x2160_12546"

    println("Target Playlist URL: $playlistFileUrl")
    println()
    println("Downloading:")

    val tmpFile = outputFile.parent.resolve("${outputFile.fileName}.tmp")
    val urls = URL(playlistFileUrl)
        .useLines { lines ->
            val baseUrl = playlistFileUrl.substringBeforeLast('/')
            lines.filterNot { it.startsWith('#') }
                .map { URI("$baseUrl/$it").toURL() }
                .toList()
        }

    tmpFile.outputStream(CREATE, TRUNCATE_EXISTING)
        .use { output ->
            val total = urls.size
            urls.asSequence()
                .onEachIndexed { i, url -> println("${i + 1} / $total: $url") }
                .forEach { url -> url.openStream().use { input -> input.copyTo(output) } }
        }

    println()

    println("Encoding...")

    val multimediaObjects = listOf(MultimediaObject(tmpFile.toFile()))
    val encodingAttributes = EncodingAttributes()
        .apply {
            setAudioAttributes(AudioAttributes().apply {
                setCodec(AudioAttributes.DIRECT_STREAM_COPY)
            })
            setVideoAttributes(VideoAttributes().apply {
                setCodec(VideoAttributes.DIRECT_STREAM_COPY)
            })
        }
    Encoder().encode(multimediaObjects, outputFile.toFile(), encodingAttributes)

    Files.delete(tmpFile)
    println("Done!")
}

private inline fun <R> URL.useLines(block: (Stream<String>) -> R): R {
    return useLines(Charset.defaultCharset(), block)
}

private inline fun <R> URL.useLines(charset: Charset, block: (Stream<String>) -> R): R {
    return openStream().use { input ->
        InputStreamReader(input, charset).use { reader ->
            BufferedReader(reader).use { it.lines().use(block) }
        }
    }
}

@Serializable
private data class VideoData(
    val id: Long,
    val title: String?,
    val description: String?,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String?,
    @SerialName("video_balancer")
    val videoBalancer: VideoBalancer,
)

@Serializable
private data class VideoBalancer(
    val default: String,
    val m3u8: String?
)

inline fun <T> Stream<T>.filterNot(crossinline predicate: (T) -> Boolean): Stream<T> = this.filter { !predicate(it) }

private interface Retriable<S> {

    operator fun <R> invoke(block: S.() -> R): R

}

private fun Retriable(
    retries: Long = Long.MAX_VALUE,
    retryDuration: Duration = 1.seconds
): Retriable<Unit> {
    return Retriable(Unit, retries, retryDuration)
}

private fun <S> Retriable(
    root: S,
    retries: Long = Long.MAX_VALUE,
    retryDuration: Duration = 1.seconds
): Retriable<S> {
    return object : Retriable<S> {
        override fun <R> invoke(block: S.() -> R): R {
            var tries = 0
            lateinit var lastException: Exception
            while (tries < retries) {
                try {
                    return root.block()
                } catch (e: Exception) {
                    println(e.message)
                    lastException = e
                        .apply {
                            if (tries > 0) {
                                addSuppressed(lastException)
                            }
                        }
                    if (++tries < retries) {
                        Thread.sleep(retryDuration.inWholeMilliseconds)
                    }
                }
            }
            lastException.printStackTrace()
            throw lastException
        }
    }
}
