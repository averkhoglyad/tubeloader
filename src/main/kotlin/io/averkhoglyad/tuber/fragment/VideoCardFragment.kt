package io.averkhoglyad.tuber.fragment

import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import io.averkhoglyad.tuber.util.noop1
import tornadofx.*

typealias CallbackFn = (VideoInfo) -> Unit

class VideoCardFragment : ListCellFragment<VideoInfo>() {

    private var onDownloadFn: CallbackFn = noop1

    override val root = vbox {
        hbox {
            imageview(itemProperty.select { it.details().thumbnails().firstOrNull().toProperty() }) {
                prefWidth(100.0)
                maxHeight(100.0)
            }
            vbox {
                label(itemProperty.select { it.details().title().toProperty() })
                label(itemProperty.select { it.details().author().toProperty() })
                label(itemProperty.select { "${it.details().lengthSeconds()} sec".toProperty() })
            }
        }
        hbox {
            button("Download") {
                disableWhen(itemProperty.isNull)
                action {
                    fire(DownloadRequestEvent(item, item.bestVideoWithAudioFormat()))
                    onDownloadFn(item)
                }
            }
        }
    }

    fun onDownload(fn: CallbackFn) {
        this.onDownloadFn = fn
    }
}

data class DownloadRequestEvent(val video: VideoInfo, val format: VideoFormat) : FXEvent()
