package io.averkhoglyad.tuber.layout

import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.AudioFormat
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import io.averkhoglyad.tuber.controller.YoutubeVideoController
import io.averkhoglyad.tuber.data.DownloadTask
import io.averkhoglyad.tuber.fragment.DownloadRequest
import io.averkhoglyad.tuber.util.consumeCloseRequest
import io.averkhoglyad.tuber.util.log4j
import io.averkhoglyad.tuber.view.DownloadsStatusView
import io.averkhoglyad.tuber.view.QueryEvent
import io.averkhoglyad.tuber.view.QueryView
import io.averkhoglyad.tuber.view.VideosListView
import javafx.stage.FileChooser
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.javafx.JavaFx
import tornadofx.*
import java.io.File
import java.nio.file.Path

class MainLayout : View("Tuber - Youtube downloader") {

    private val logger by log4j()

    private var initDirToImportFile = File("./")

    private val controller: YoutubeVideoController by inject()

    private val queryView by inject<QueryView>()
    private val listView by inject<VideosListView>()
    private val statusView by inject<DownloadsStatusView>()

    override val root = borderpane {
        top {
            this += queryView
        }
        center {
            this += listView
        }
        bottom {
            this += statusView
        }
    }

    init {
        initPrimaryStage()
        bindEvents()
    }

    private fun initPrimaryStage() {
        primaryStage.apply {
            minWidth = 900.0
            minHeight = 600.0
            width = 900.0
            height = 600.0
            consumeCloseRequest { window ->
                confirm("Are you sure you want to close application?") {
                    window.close()
                }
            }
        }
    }

    private fun bindEvents() {
        subscribe<QueryEvent> {
            loadVideoByQuery(it.query)
        }
        subscribe<DownloadRequest> { (video, videoFormat, audioFormat) ->
            selectFileToSave(videoFormat.extension().value())
                ?.let { target -> downloadVideo(target, video, videoFormat, audioFormat) }
        }
    }

    private fun loadVideoByQuery(query: String) {
        GlobalScope.launch(Dispatchers.JavaFx) {
            queryView.searching = true
            try {
                val videoId = controller.parseVideoIdFromUrl(query) ?: query
                if (videoId.isBlank()) {
                    information("No video ID")
                } else {
                    controller.loadVideoInfo(videoId).await()
                        ?.let { listView.addVideo(it) } ?: warning("Video not found")
                }
            } finally {
                queryView.searching = false
            }
        }
    }

    private fun selectFileToSave(ext: String): Path? {
        val filters = arrayOf(FileChooser.ExtensionFilter("${ext.uppercase()} file", "*.$ext"))
        val file: File = chooseFile(owner = primaryStage,
                                    mode = FileChooserMode.Save,
                                    filters = filters, 
                                    initialDirectory = initDirToImportFile)
            .firstOrNull() ?: return null
        initDirToImportFile = file.parentFile
        if (file.name.endsWith(".$ext")) {
            return file.toPath()
        }
        return file.toPath().parent.resolve(file.name + ".$ext")
    }

    private fun downloadVideo(target: Path, video: VideoInfo, videoFormat: VideoFormat, audioFormat: AudioFormat?) {
        val task = DownloadTask(video)
        statusView.addDownload(task)
        val channel = controller.downloadVideo(target, videoFormat, audioFormat)
        GlobalScope.launch(Dispatchers.JavaFx) {
            channel.consumeEach(task.progress::set)
            channel.invokeOnClose {
                statusView.dropDownload(task)
            }
        }
    }
}
