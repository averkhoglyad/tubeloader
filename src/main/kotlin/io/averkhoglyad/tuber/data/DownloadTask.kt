package io.averkhoglyad.tuber.data

import com.github.kiulian.downloader.model.videos.VideoInfo
import javafx.beans.property.SimpleDoubleProperty
import java.util.UUID

class DownloadTask(videoInfo: VideoInfo) {
    val id: UUID = UUID.randomUUID()
    val videoInfo: VideoInfo = videoInfo
    val progress: SimpleDoubleProperty = SimpleDoubleProperty()
}
