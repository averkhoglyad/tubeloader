package io.averkhoglyad.tuber.fragment

import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import io.averkhoglyad.tuber.util.CallbackFn
import io.averkhoglyad.tuber.util.fontawesome
import io.averkhoglyad.tuber.util.noop1
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.text.FontWeight
import org.controlsfx.glyphfont.FontAwesome
import tornadofx.*
import java.util.*

class VideoCardFragment : ListCellFragment<VideoInfo>() {

    private val video by itemProperty
    private var onDownloadFn: CallbackFn<VideoInfo> = noop1

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
                        itemProperty.select { it.videoWithAudioFormats().reversed().toProperty() }
                            .onChange {
                                this@menubutton.items.clear()
                                it?.forEach { format ->
                                        item("${format.extension().value().uppercase(Locale.getDefault())} ${format.qualityLabel()}") {
                                            action {
                                                fire(DownloadRequestEvent(item, item.bestVideoWithAudioFormat()))
                                                onDownloadFn(item)
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

    fun onDownload(fn: CallbackFn<VideoInfo>) {
        this.onDownloadFn = fn
    }
}

data class DownloadRequestEvent(val video: VideoInfo, val format: VideoFormat) : FXEvent()
