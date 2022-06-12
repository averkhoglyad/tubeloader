package io.averkhoglyad.tuber.data

import com.github.kiulian.downloader.model.videos.VideoInfo
import io.averkhoglyad.tuber.util.CallbackFn
import io.averkhoglyad.tuber.util.noCallback
import javafx.beans.binding.ObjectExpression
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.beans.property.ReadOnlyDoubleWrapper
import javafx.beans.property.ReadOnlyObjectWrapper
import tornadofx.select
import tornadofx.toProperty
import java.nio.file.Path
import java.util.*

class DownloadTask(videoInfo: VideoInfo,
                   targetPath: Path,
                   cancelFn: CallbackFn<VideoInfo> = noCallback) {

    val id: UUID = UUID.randomUUID()
    val videoInfo: VideoInfo = videoInfo
    val targetPath: Path = targetPath

    private val progressWrapper = ReadOnlyDoubleWrapper(-1.0)
    val progress = progressWrapper.readOnlyProperty

    private val statusWrapper = ReadOnlyObjectWrapper(TaskStatus.IN_PROGRESS)
    val status = statusWrapper.readOnlyProperty

    private val messageWrapper = ReadOnlyObjectWrapper<String?>(null)
    val message = messageWrapper.readOnlyProperty

    private val cancelFn: CallbackFn<VideoInfo> = cancelFn

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
        this.cancelFn(videoInfo)
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
