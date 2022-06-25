package io.averkhoglyad.tubeloader.layout.view

import io.averkhoglyad.tubeloader.data.DownloadTask
import io.averkhoglyad.tubeloader.layout.fragment.TaskLineFragment
import io.averkhoglyad.tubeloader.util.modifyCell
import org.controlsfx.control.TaskProgressView
import tornadofx.*

class TasksListView : View() {

    private val tasks = observableListOf<DownloadTask>()

    override val root = borderpane {
        styleClass += "task-progress-view"
        center {
            listview(tasks) {
                setPrefSize(500.0, 400.0)
                placeholder = label("No download tasks")
                isFocusTraversable = false
                cellFragment(TaskLineFragment::class)
                modifyCell { cell ->
                    cell.styleClass.setAll("task-list-cell-empty")
                    cell.itemProperty().onChange {
                        if (it == null) {
                            cell.styleClass.setAll("task-list-cell-empty")
                        } else {
                            cell.styleClass.setAll("task-list-cell")
                        }
                    }
                }
            }
        }
    }

    init {
        importStylesheet(TaskProgressView::class.java.getResource("taskprogressview.css").toExternalForm())
    }

    fun addTask(task: DownloadTask) {
        tasks.add(task)
    }

    fun addTasks(tasks: List<DownloadTask>) {
        this.tasks.addAll(tasks)
    }
}
