package io.averkhoglyad.tuber.layout.fragment

import com.github.kiulian.downloader.model.videos.VideoInfo
import com.github.kiulian.downloader.model.videos.formats.VideoFormat
import io.averkhoglyad.tuber.util.CallbackFn
import io.averkhoglyad.tuber.util.fontawesome
import io.averkhoglyad.tuber.util.log4j
import io.averkhoglyad.tuber.util.noCallback
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.text.FontWeight
import org.apache.logging.log4j.util.Supplier
import org.controlsfx.glyphfont.FontAwesome
import tornadofx.*
import java.util.*

class VideoCardFragment : ListCellFragment<VideoInfo>() {

    private val logger by log4j()

    private var onDownloadFn: CallbackFn<DownloadRequest> = noCallback

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
        logger.debug("Detecting supported formats for video: {}", video.details().videoId())

        val bestVideoWithAudioFormat = video.bestVideoWithAudioFormat()
        val videoWithAudioFormats = video.videoWithAudioFormats()

        logger.debug("-- detected best video with audio format {}", Supplier { bestVideoWithAudioFormat.asString() })
        logger.debug("-- detected all video with audio formats: {}", Supplier { videoWithAudioFormats.asString() })

        val highQualityVideoWithoutAudioFormats = video.videoFormats()
            .filter { it.videoQuality().ordinal > bestVideoWithAudioFormat.videoQuality().ordinal }

        logger.debug("-- detected high quality without audio formats: {}", Supplier { highQualityVideoWithoutAudioFormats.asString() })

        val formats = highQualityVideoWithoutAudioFormats.filter { it.extension().value() != "webm" } + videoWithAudioFormats

        logger.debug("-- detected high quality without audio formats: {}", Supplier { highQualityVideoWithoutAudioFormats.asString() })

        return formats.sortedByDescending { it.videoQuality().ordinal }
    }

    private fun requestDownload(format: VideoFormat) {
        val dwReq = DownloadRequest(item, format)
        fire(dwReq)
        onDownloadFn(dwReq)
    }

    fun onDownload(fn: CallbackFn<DownloadRequest>) {
        this.onDownloadFn = fn
    }
}

private fun VideoFormat.asString() = "${qualityLabel()} ${extension().value()} ${mimeType()}"

private fun List<VideoFormat>.asString() = this.takeUnless { it.isEmpty() }
    ?.let { it.asSequence().map(VideoFormat::asString).joinToString("\n  - ", prefix = "\n  - ") }
    ?: "<empty>"

data class DownloadRequest(val video: VideoInfo, val videoFormat: VideoFormat) : FXEvent()
