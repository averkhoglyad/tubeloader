package io.averkhoglyad.tuber.layout

import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import io.averkhoglyad.tuber.controller.YoutubeVideoController
import io.averkhoglyad.tuber.layout.fragment.DownloadRequest
import io.averkhoglyad.tuber.layout.view.*
import io.averkhoglyad.tuber.util.consumeCloseRequest
import io.averkhoglyad.tuber.util.log4j
import javafx.stage.FileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import tornadofx.*
import java.io.File
import java.nio.file.Path

class MainLayout : View("Tuber - Youtube downloader") {

    private val logger by log4j()

    private var initDirToImportFile = File("./")

    private val controller: YoutubeVideoController by inject()

    private val queryView by inject<QueryView>()
    private val videosView by inject<VideosListView>()
    private val tasksView by inject<TasksListView>()
    private val statusView by inject<DownloadsStatusView>()

    override val root = borderpane {
        top {
            this += queryView
        }
        center {
            splitpane {
                this += videosView
                this += tasksView
//                tasksView.root.hide()
            }
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
                confirm(title = "Confirm exit", header = "Are you sure you want to exit?") {
                    window.close()
                }
            }
        }
    }

    private fun bindEvents() {
        subscribe<QueryEvent> {
            loadVideoByQuery(it.query)
        }
        subscribe<DownloadRequest> { (video, videoFormat) ->
            selectFileToSave(videoFormat.extension().value())
                ?.let { target -> downloadVideo(target, video, videoFormat) }
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
                    controller.loadVideoInfoAsync(videoId).await()
                        ?.let { videosView.addVideo(it) } ?: warning("Video not found")
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

    private fun downloadVideo(target: Path, video: VideoInfo, videoFormat: VideoFormat) {
        val task = controller.downloadVideo(target, video, videoFormat)
        statusView.addDownload(task)
        tasksView.addTask(task)
    }
}
