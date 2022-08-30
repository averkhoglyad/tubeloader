package io.averkhoglyad.tubeloader.layout

import io.averkhoglyad.tubeloader.controller.YoutubeVideoController
import io.averkhoglyad.tubeloader.data.DownloadOption
import io.averkhoglyad.tubeloader.data.VideoDetails
import io.averkhoglyad.tubeloader.layout.fragment.DownloadRequest
import io.averkhoglyad.tubeloader.layout.view.*
import io.averkhoglyad.tubeloader.util.consumeCloseRequest
import io.averkhoglyad.tubeloader.util.log4j
import io.averkhoglyad.tubeloader.util.requestClose
import javafx.scene.image.Image
import javafx.stage.FileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import tornadofx.*
import java.io.File
import java.nio.file.Path

class MainLayout : View("Tubeloader - Download videos from Youtube") {

    private val logger by log4j()

    private var initDirToImportFile = File("./")

    private val controller: YoutubeVideoController by inject()

    private val queryView by inject<QueryView>()
    private val videosView by inject<VideosListView>()
    private val tasksView by inject<TasksListView>()
    private val statusView by inject<DownloadsStatusView>()
    private val topMenuView by inject<TopMenuView>()

    override val root = borderpane {
        top {
            vbox {
                this += topMenuView
                this += queryView
            }
        }
        center {
            splitpane {
                this += videosView
                this += tasksView
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
        subscribe<DownloadRequest> { (video, option) ->
            selectFileToSave(video.title.removeUnsupportedChars(), option.extension)
                ?.let { target -> downloadVideo(target, video, option) }
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
                    controller.loadVideoInfoAsync(videoId)
                        .await()
                        ?.let { videosView.addVideo(it) } ?: warning("Video not found")
                }
            } finally {
                queryView.searching = false
            }
        }
    }

    private fun selectFileToSave(initialFileName: String, ext: String): Path? {
        val filters = arrayOf(FileChooser.ExtensionFilter("${ext.uppercase()} file", "*.$ext"))
        val file: File = chooseFile(owner = primaryStage, mode = FileChooserMode.Save, filters = filters) {
            this@chooseFile.initialDirectory = initDirToImportFile
            this@chooseFile.initialFileName = initialFileName
        }.firstOrNull() ?: return null
        initDirToImportFile = file.parentFile
        if (file.name.endsWith(".$ext")) {
            return file.toPath()
        }
        return file.toPath().parent.resolve(file.name + ".$ext")
    }

    private fun downloadVideo(target: Path, video: VideoDetails, option: DownloadOption) {
        val task = controller.downloadVideoAsync(target, video, option)
        statusView.addDownload(task)
        tasksView.addTask(task)
    }
}

private fun String.removeUnsupportedChars() = this
    .replace("[\\\\:\\*\\/\\?|<>]".toRegex(), " ")
    .replace("\\s+".toRegex(), " ")
