package io.averkhoglyad.tuber.data

import com.github.kiulian.downloader.model.videos.VideoInfo
import io.averkhoglyad.tuber.util.CallbackFn
import io.averkhoglyad.tuber.util.noCallback
import javafx.beans.binding.BooleanExpression
import javafx.beans.binding.DoubleExpression
import javafx.beans.binding.ObjectExpression
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.beans.property.ReadOnlyObjectWrapper
import tornadofx.onChange
import tornadofx.select
import tornadofx.toProperty
import java.nio.file.Path
import java.util.*

class DownloadTask(videoInfo: VideoInfo,
                   targetPath: Path,
                   progress: DoubleExpression,
                   status: ObjectExpression<TaskStatus>,
                   cancelFn: CallbackFn<VideoInfo> = noCallback) {

    val id: UUID = UUID.randomUUID()

    val videoInfo: VideoInfo = videoInfo
    val targetPath: Path = targetPath
    val progress: DoubleExpression = progress

    private val statusWrapper: ReadOnlyObjectWrapper<TaskStatus>
    val status: ObjectExpression<TaskStatus>

    private val cancelFn: CallbackFn<VideoInfo> = cancelFn

    val isFinished: BooleanExpression
    val isSucceded: BooleanExpression
    val isCanceled: BooleanExpression
    val isFailed: BooleanExpression

    init {
        statusWrapper = ReadOnlyObjectWrapper(status.value)
        status.onChange { statusWrapper.set(it) }
        this.status = statusWrapper.readOnlyProperty
        isFinished = this.status.extractBooleanPropertyFromStatus { it.isFinished }
        isSucceded = this.status.extractBooleanPropertyFromStatus { it == TaskStatus.DONE }
        isCanceled = this.status.extractBooleanPropertyFromStatus { it == TaskStatus.CANCELED }
        isFailed = this.status.extractBooleanPropertyFromStatus { it == TaskStatus.FAILED }
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

private inline fun ObjectExpression<TaskStatus>.extractBooleanPropertyFromStatus(crossinline fn: (TaskStatus) -> Boolean): BooleanExpression {
    val booleanWrapper = ReadOnlyBooleanWrapper(false)
    booleanWrapper.bind(this.select { fn(it).toProperty() })
    return booleanWrapper.readOnlyProperty
}
