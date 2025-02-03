package io.averkhoglyad.tubeloader.data

import io.averkhoglyad.tubeloader.util.CallbackFn
import io.averkhoglyad.tubeloader.util.noCallback
import javafx.beans.binding.ObjectExpression
import javafx.beans.property.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import tornadofx.select
import tornadofx.toProperty
import java.nio.file.Path

class DownloadTask(videoDetails: VideoDetails,
                   targetPath: Path,
                   cancelFn: CallbackFn<VideoDetails> = noCallback) {

    val videoDetails: VideoDetails = videoDetails
    val targetPath: Path = targetPath
    val startedAt: Instant = Clock.System.now()

    private val progressWrapper = ReadOnlyDoubleWrapper(-1.0)
    val progress = progressWrapper.readOnlyProperty

    private val statusWrapper = ReadOnlyObjectWrapper(TaskStatus.IN_PROGRESS)
    val status: ReadOnlyObjectProperty<TaskStatus> = statusWrapper.readOnlyProperty

    private val messageWrapper = ReadOnlyObjectWrapper<String?>(null)
    val message: ReadOnlyObjectProperty<String?> = messageWrapper.readOnlyProperty

    private val cancelFn: CallbackFn<VideoDetails> = cancelFn

    val isFinished = status.extractBooleanPropertyFromStatus { it.isFinished }
    val isSucceeded = status.extractBooleanPropertyFromStatus { it == TaskStatus.DONE }
    val isCanceled = status.extractBooleanPropertyFromStatus { it == TaskStatus.CANCELED }
    val isFailed = status.extractBooleanPropertyFromStatus { it == TaskStatus.FAILED }

    fun progress(progress: Double) {
        progressWrapper.set(progress)
    }

    fun status(status: TaskStatus) {
        statusWrapper.set(status)
    }

    fun status(status: TaskStatus, message: String) {
        statusWrapper.set(status)
        messageWrapper.set(message)
    }

    fun cancel() {
        statusWrapper.set(TaskStatus.CANCELING)
        this.cancelFn(videoDetails)
    }
}

enum class TaskStatus(val isFinished: Boolean) {

    IN_PROGRESS(false), DONE(true), CANCELING(false), CANCELED(true), FAILED(true);

    override fun toString(): String {
        return this.name.replace('_', ' ').lowercase()
    }
}

private inline fun ObjectExpression<TaskStatus>.extractBooleanPropertyFromStatus(crossinline fn: (TaskStatus) -> Boolean): ReadOnlyBooleanProperty {
    val booleanWrapper = ReadOnlyBooleanWrapper(false)
    booleanWrapper.bind(this.select { fn(it).toProperty() })
    return booleanWrapper.readOnlyProperty
}
