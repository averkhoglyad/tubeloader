package io.averkhoglyad.tubeloader.layout.view

import javafx.scene.paint.Color
import javafx.scene.text.Font
import tornadofx.*

class AboutPopup: View("About") {

    override val root = vbox(10) {
        padding = insets(20.0)
        minWidth = 300.0
        maxWidth = 400.0
        minHeight = 200.0
        hbox(20) {
            spacer()
            label("Tubeloader") {
                textFill = Color.DIMGRAY
                style {
                    font = Font(20.0)
                }
            }
            spacer()
        }
        hbox {
            textflow {
                text("Simple tool to download videos from YouTube\n")
                text("""
                    |This program is free software; it is distributed in the hope that it will be useful.
                    |User could use it "AS IS" WITHOUT WARRANTY OF ANY KIND
                    |""".trimMargin())
            }
        }
        hbox {
            textflow {
                text("License: ")
                hyperlink("GPL3 LICENSE") {
                    isFocusTraversable = false
                    action {
                        hostServices.showDocument("https://github.com/averkhoglyad/tubeloader/blob/master/LICENSE")
                        isVisited = false
                    }
                }
                text("\n")
                text("Sourcecode: ")
                hyperlink("GitHub") {
                    isFocusTraversable = false
                    action {
                        hostServices.showDocument("https://github.com/averkhoglyad/tubeloader")
                        isVisited = false
                    }
                }
            }
        }
    }
}