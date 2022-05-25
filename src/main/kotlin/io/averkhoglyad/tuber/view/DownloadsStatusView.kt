package io.averkhoglyad.tuber.view

import com.github.kiulian.downloader.model.videos.VideoInfo
import io.averkhoglyad.tuber.data.DownloadTask
import io.averkhoglyad.tuber.util.onChange
import javafx.beans.binding.DoubleExpression
import javafx.beans.property.DoubleProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import tornadofx.*
import tornadofx.controlsfx.statusbar
import java.util.concurrent.atomic.AtomicInteger

class DownloadsStatusView : View() {

    private val downloads = observableListOf<DownloadTask>()

    override val root = hbox {
        label {
            GlobalScope.launch(Dispatchers.JavaFx) {
                val counter = AtomicInteger()
                ticker(100)
                    .consumeEach { this@label.text = "${counter.getAndIncrement() % 10}" }
            }
        }
        statusbar {
            fitToWidth(this@hbox)
            downloads.onChange {
                if (it.list.isEmpty()) {
                    this@statusbar.text = ""
                    this@statusbar.progressProperty().unbind()
                    this@statusbar.progress = 0.0
                } else {
                    this@statusbar.text = "Download"
                    val reduce: DoubleExpression = it.list
                        .fold(0.0.toProperty() as DoubleExpression) { acc, item -> acc + item.progress }
                    this@statusbar.progressProperty().bind(reduce / it.list.size)
                }
            }
        }
    }

    fun addDownload(task: DownloadTask) {
        downloads.add(task)
    }

    fun dropDownload(task: DownloadTask) {
        if (downloads.all { el -> el.progress >= 1 }) {
            downloads.clear()
        }
    }
}