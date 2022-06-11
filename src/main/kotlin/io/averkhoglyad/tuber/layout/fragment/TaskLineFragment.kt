package io.averkhoglyad.tuber.layout.fragment

import io.averkhoglyad.tuber.data.DownloadTask
import io.averkhoglyad.tuber.data.TaskStatus
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import tornadofx.*


class TaskLineFragment : ListCellFragment<DownloadTask>() {

    private val taskStatus = SimpleObjectProperty<TaskStatus?>(null)

    override val root = borderpane {
        center {
            vbox(4.0) {
                label {
                    styleClass += "task-title"
                    textProperty().bind( itemProperty.select { it.videoInfo.details().title().toProperty() } )
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
                label {
                    styleClass += "task-message"
                    textProperty().bind( itemProperty.select { it.targetPath.fileName.toString().toProperty() } )
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
                        if (item != null) {
                            item.cancel()
                        }
                    }
                }
            }
        }
    }

    init {
        taskStatus.set(item?.status?.value)
        item?.let { taskStatus.bind(item.status) }
        itemProperty.onChange {
            taskStatus.unbind()
            it?.status?.let { taskStatus.bind(it) }
        }
    }
}

private fun String.toTitleCase(): String {
    val sb: StringBuilder = StringBuilder(lowercase())
    sb.setCharAt(0, sb[0].uppercaseChar())
    return sb.toString()
}