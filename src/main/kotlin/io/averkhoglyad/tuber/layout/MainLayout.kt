package io.averkhoglyad.tuber.layout

import com.github.kiulian.downloader.YoutubeDownloader
import com.github.kiulian.downloader.downloader.YoutubeProgressCallback
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload
import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import io.averkhoglyad.tuber.util.greaterThanOrEqual
import io.averkhoglyad.tuber.util.log4j
import io.averkhoglyad.tuber.util.quite
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.image.Image
import javafx.stage.FileChooser
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.javafx.JavaFx
import tornadofx.*
import java.io.File
import java.io.OutputStream
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class MainLayout : View("Tuber - Youtube downloader") {

    private val logger by log4j()

    private val downloader = YoutubeDownloader()

    private val searchField = SimpleStringProperty()
    private val searching = SimpleBooleanProperty()
    private val details = SimpleObjectProperty<VideoInfo>()
    private val progress = SimpleIntegerProperty(-1)

    private var initDirToImportFile = File("./")

    override val root = vbox {
        val self = this
        hbox {
            label("URL")
            textfield(searchField) {
                disableWhen(searching)
            }
        }
        button("Parse") {
            fitToWidth(self)
            disableWhen(searching)
            action {
                try {
                    searching.set(true)
                    val videoId = searchField.get().takeIf { it.isNotBlank() }
                        ?.let { it.trim() }
                        ?.let { parseIdFromQueryIfUrl(searchField.get()) }
                        ?: searchField.get()

                    if (videoId.isNotBlank()) {
                        val info = GlobalScope.async(Dispatchers.IO) {
                            videoInfo(videoId)
                        }
                        GlobalScope.launch(Dispatchers.JavaFx) {
                            details.set(info.await())
                        }
                    } else {
                        information("No video ID")
                    }
                } finally {
                    searching.set(false)
                }
            }
        }
        vbox {
            removeWhen(details.isNull)
            hbox {
                imageview {
                    details.onChange {
                        if (it == null) return@onChange
                        val url = it.details().thumbnails().firstOrNull()
                        this@imageview.image = Image(url, true)
                    }
                }
                vbox {
                    label {
                        details.onChange {
                            if (it == null) return@onChange
                            this@label.text = it.details().title()
                        }
                    }
                    label {
                        details.onChange {
                            if (it == null) return@onChange
                            this@label.text = it.details().author()
                        }
                    }
                    label {
                        details.onChange {
                            if (it == null) return@onChange
                            this@label.text = "${it.details().lengthSeconds()} sec"
                        }
                    }
                }
            }
            hbox {
//                combobox {  }
                button("Download") {
                    disableWhen(searching)
                    action {
                        val format = details.get().bestVideoWithAudioFormat()
                        selectFileToSave(format.extension().value())
                            ?.let { target->
                                progress.set(0)
                                GlobalScope.launch {
                                    Files.newOutputStream(target).use { out ->
                                        runBlocking(Dispatchers.IO) {
                                            try {
                                                val ch = downloader.downloadTo(format, out)
                                                ch.consumeEach {
                                                    withContext(Dispatchers.JavaFx) {
                                                        progress.set(it)
                                                    }
                                                }
                                            } finally {
                                            }
                                        }
                                    }
                                    progress.set(-1)
                                }
                            }
                    }
                }
            }
        }
        label {
            removeWhen(progress lt (0))
            fitToWidth(self)
            progress.onChange { this@label.text = "Progress: $it%" }
        }
        label {
            fitToWidth(self)
            GlobalScope.launch(Dispatchers.JavaFx) {
                val counter = AtomicInteger()
                ticker(100)
                    .consumeEach { this@label.text = "${counter.getAndIncrement() % 10}" }
            }
        }
    }

    private fun parseIdFromQueryIfUrl(query: String): String? {
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

    private fun videoInfo(videoId: String): VideoInfo {
        val request = RequestVideoInfo(videoId)
        val response = downloader.getVideoInfo(request)
        return response.data()
    }

    private fun selectFileToSave(ext: String): Path? {
        val filters = arrayOf(FileChooser.ExtensionFilter("${ext.uppercase()} file", "*.$ext"))

        val file: File = chooseFile(owner = primaryStage, mode = FileChooserMode.Save, filters = filters) {
            initialDirectory = initDirToImportFile
        }.firstOrNull() ?: return null

        initDirToImportFile = file.parentFile

        if (file.name.endsWith(".$ext")) {
            return file.toPath()
        }
        return file.toPath().parent.resolve(file.name + ".$ext")
    }

    init {
        primaryStage.apply {
//            minWidth = 600.0
//            minHeight = 400.0
//            width = 900.0
//            height = 600.0
//            consumeCloseRequest { window ->
//                confirm("Are you sure you want to close ChessManager?") {
//                    window.close()
//                }
//            }
        }
    }
}

private suspend fun YoutubeDownloader.downloadTo(format: VideoFormat, out: OutputStream): Channel<Int> {
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
    GlobalScope.launch(Dispatchers.IO) { downloadVideoStream(req) }
    return ch
}