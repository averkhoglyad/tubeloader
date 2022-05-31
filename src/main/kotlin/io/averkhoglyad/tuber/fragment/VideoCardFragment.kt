package io.averkhoglyad.tuber.fragment

import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.AudioFormat
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import com.github.kiulian.downloader.model.videos.formats.VideoWithAudioFormat
import io.averkhoglyad.tuber.util.fontawesome
import io.averkhoglyad.tuber.util.noop3
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.text.FontWeight
import org.controlsfx.glyphfont.FontAwesome
import tornadofx.*
import java.util.*

typealias CallbackFn = (video: VideoInfo, videoFormat: VideoFormat, audioFormat: AudioFormat?) -> Unit

class VideoCardFragment : ListCellFragment<VideoInfo>() {

    private var onDownloadFn: CallbackFn = noop3

    override val root = vbox {
        borderpane {
            left {
                imageview(itemProperty.select { it.details().thumbnails().firstOrNull().toProperty() }) {
                    prefWidth(250.0)
                    maxHeight(250.0)
                }
            }
            center {
                vbox(5.0) {
                    padding = Insets(5.0, 10.0, 5.0, 10.0)
                    label(itemProperty.select { it.details().title().toProperty() }) {
                        style {
                            fontWeight = FontWeight.EXTRA_BOLD
                        }
                    }
                    hbox {
                        label(itemProperty.select { it.details().author().toProperty() }) {
                            style {
                                textFill = c("006caa")
                            }
                        }
                        spacer()
                        label(itemProperty.select { parseDuration(it.details().lengthSeconds()).toProperty() }) {
                            style {
                                textFill = c("#a94442")
                            }
                        }
                    }
                }
            }
            right {
                vbox(5.0, Pos.CENTER) {
                    menubutton(graphic = fontawesome(FontAwesome.Glyph.DOWNLOAD)) {
                        disableWhen(itemProperty.isNull)
                        itemProperty.select { it.toProperty() }
                            .onChange { video ->
                                this@menubutton.items.clear()
                                if (video == null) {
                                    return@onChange
                                }
                                detectSupportedFormats(video)
                                    .forEach { format ->
                                        item(formatLabel(format)) {
                                            action {
                                                requestDownload(format)
                                            }
                                        }
                                    }
                            }
                    }
                }
            }
        }
    }

    private fun parseDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val seconds = seconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun formatLabel(format: VideoFormat): String {
        return "${format.extension().value().uppercase(Locale.getDefault())} ${format.qualityLabel()}"
    }

    private fun detectSupportedFormats(video: VideoInfo): List<VideoFormat> {
        val videoWithAudioFormats = video.videoWithAudioFormats()
        val bestVideoWithAudioFormat = video.bestVideoWithAudioFormat()
        val highQualityVideoWithoutAudioFormats = video.videoFormats()
            .filter { it.extension().value() != "webm" }
            .filter { it.videoQuality().ordinal > bestVideoWithAudioFormat.videoQuality().ordinal }
        val formats = highQualityVideoWithoutAudioFormats + videoWithAudioFormats
        return formats.sortedByDescending { it.videoQuality().ordinal }
    }

    private fun requestDownload(format: VideoFormat) {
        val dwReq: DownloadRequest
        if (format is VideoWithAudioFormat) {
            dwReq = DownloadRequest(item, format)
        } else {
            dwReq = DownloadRequest(item, format, item.bestAudioFormat())
        }
        fire(dwReq)
        onDownloadFn(dwReq.video, dwReq.videoFormat, dwReq.audioFormat)
    }

    fun onDownload(fn: CallbackFn) {
        this.onDownloadFn = fn
    }
}

data class DownloadRequest(val video: VideoInfo,
                           val videoFormat: VideoFormat,
                           val audioFormat: AudioFormat?) : FXEvent() {
    constructor(video: VideoInfo, videoFormat: VideoWithAudioFormat) : this(video, videoFormat, null)
}
