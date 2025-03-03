package io.averkhoglyad.tubeloader.layout.fragment

import io.averkhoglyad.tubeloader.data.DownloadTask
import io.averkhoglyad.tubeloader.data.TaskStatus
import io.averkhoglyad.tubeloader.util.toTitleCase
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.datetime.Clock
import tornadofx.*
import kotlin.time.Duration

class TaskLineFragment : ListCellFragment<DownloadTask>() {

    private val taskStatus = SimpleObjectProperty<TaskStatus?>(null)

    private val timeFlow = flow {
        while (taskStatus.get() == null || taskStatus.get() == TaskStatus.IN_PROGRESS) {
            emit(Clock.System.now())
            delay(500)
        }
    }

    override val root = borderpane {
        center {
            vbox(4.0) {
                label {
                    styleClass += "task-title"
                    textProperty().bind( itemProperty.select { it.videoDetails.title.toProperty() } )
                }
                label {
                    styleClass += "task-message"
                    textProperty().bind( itemProperty.select { it.targetPath.fileName.toString().toProperty() } )
                }
                progressbar {
                    styleClass += "task-progress-bar"
                    maxHeight = 8.0
                    maxWidth = Double.MAX_VALUE
                    progressProperty().bind(itemProperty.select { it.progress })
//                    style {
//                        accentColor = c()
//                    }
//                    visibleWhen { taskStatus.isEqualTo(TaskStatus.IN_PROGRESS) }
                }
                hbox {
                    spacer()
                    label {
                        styleClass += "task-message"
                        timeFlow
                            .onEach { time ->
                                itemProperty.get()
                                    ?.startedAt
                                    ?.let { time - it }
                                    ?.let { this@label.text = "Spent: ${it.toFineString()}" }
                            }
                            .flowOn(Dispatchers.JavaFx)
                            .launchIn(GlobalScope)
                    }
                }
            }
        }
        right  {
            group {
                borderpaneConstraints {
                    alignment = Pos.CENTER
                    margin = Insets(0.0, 0.0, 0.0, 4.0)
                }
                label(taskStatus.select { it.toString().toTitleCase().toProperty() }) {
                    removeWhen { taskStatus.isEqualTo(TaskStatus.IN_PROGRESS) }
                }
                button("Cancel") {
                    styleClass += "task-cancel-button"
                    tooltip = tooltip("Cancel Task")
                    removeWhen { taskStatus.isNotEqualTo(TaskStatus.IN_PROGRESS) }
                    action {
                        item?.cancel()
                    }
                }
            }
        }
    }

    init {
        taskStatus.set(item?.status?.value)
        item?.let { taskStatus.bind(item.status) }
        itemProperty.onChange { item ->
            taskStatus.unbind()
            item?.status?.let { taskStatus.bind(it) }
        }
    }
}

private fun Duration.toFineString(): String {
    return toComponents { hours, minutes, seconds, nanoseconds ->
        sequenceOf(hours, minutes, seconds)
            .map { "%02d".format(it) }
            .joinToString(":")
    }
}
