package io.averkhoglyad.tubeloader.layout.fragment

import javafx.scene.paint.Color
import javafx.scene.text.Font
import tornadofx.*

class AboutPopup: Fragment() {

    override val root = vbox(15) {
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
                text("This program is free software; it is distributed in the hope that it will be useful \"AS IS\" WITHOUT WARRANTY OF ANY KIND")
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