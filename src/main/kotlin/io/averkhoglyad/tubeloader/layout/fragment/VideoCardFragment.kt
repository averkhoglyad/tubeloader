package io.averkhoglyad.tubeloader.layout.fragment

import io.averkhoglyad.tubeloader.data.DownloadOption
import io.averkhoglyad.tubeloader.data.VideoDetails
import io.averkhoglyad.tubeloader.util.CallbackFn
import io.averkhoglyad.tubeloader.util.fontawesome
import io.averkhoglyad.tubeloader.util.noCallback
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.image.Image
import javafx.scene.text.FontWeight
import org.controlsfx.glyphfont.FontAwesome
import tornadofx.*
import java.net.URL
import javax.imageio.ImageIO
import kotlin.time.Duration

class VideoCardFragment : ListCellFragment<VideoDetails>() {

    private var onDownloadFn: CallbackFn<DownloadRequest> = noCallback

    override val root = vbox {
        borderpane {
            left {
                imageview {
                    itemProperty.select { it.thumbnail.toProperty() }
                        .onChange {
                            if (it != null) {
                                if (it.endsWith(".webp")) {
                                    runAsync {
                                        image = SwingFXUtils.toFXImage(ImageIO.read(URL(it)), null)
                                    }
                                } else {
                                    image = Image(it, true)
                                }
                            }
                        }

                    prefWidth(250.0)
                    maxHeight(250.0)
                }
            }
            center {
                vbox(5.0) {
                    padding = Insets(5.0, 10.0, 5.0, 10.0)
                    label(itemProperty.select { it.title.toProperty() }) {
                        style {
                            fontWeight = FontWeight.EXTRA_BOLD
                        }
                    }
                    hbox {
                        label(itemProperty.select { it.author.toProperty() }) {
                            style {
                                textFill = c("006caa")
                            }
                        }
                        spacer()
                        label(itemProperty.select { it.duration.toHumanReadableString().toProperty() }) {
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
                                video.downloadOptions
                                    .forEach {
                                        item(formatLabel(it)) {
                                            action {
                                                requestDownload(it)
                                            }
                                        }
                                    }
                            }
                    }
                }
            }
        }
    }

    private fun Duration.toHumanReadableString(): String {
        val hours = inWholeHours
        val minutes = inWholeMinutes - hours * 60
        val seconds = inWholeSeconds - (hours * 60 + minutes) * 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun formatLabel(option: DownloadOption): String {
        return "${option.extension.uppercase()} ${option.label}"
    }

    private fun requestDownload(option: DownloadOption) {
        val dwReq = DownloadRequest(item, option)
        fire(dwReq)
        onDownloadFn(dwReq)
    }

    fun onDownload(fn: CallbackFn<DownloadRequest>) {
        this.onDownloadFn = fn
    }
}

data class DownloadRequest(val video: VideoDetails, val downloadOption: DownloadOption) : FXEvent()
