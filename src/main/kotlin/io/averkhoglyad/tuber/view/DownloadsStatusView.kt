package io.averkhoglyad.tuber.view

import io.averkhoglyad.tuber.data.DownloadTask
import io.averkhoglyad.tuber.util.onChange
import javafx.beans.binding.DoubleExpression
import javafx.collections.ObservableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import org.controlsfx.control.StatusBar
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
                handleProgress(it.list)
            }
        }
    }

    private fun StatusBar.handleProgress(list: List<DownloadTask>) {
        if (list.isEmpty()) {
            this.text = ""
            this.progressProperty().unbind()
            this.progress = 0.0
        } else {
            this.text = "Download"
            val reduce: DoubleExpression = list
                .fold(0.0.toProperty() as DoubleExpression) { acc, item -> acc + item.progress + 0.001 }
            this.progressProperty().bind(reduce / list.size)
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