package io.averkhoglyad.tubeloader.layout.view

import io.averkhoglyad.tubeloader.layout.fragment.AboutPopup
import io.averkhoglyad.tubeloader.util.requestClose
import javafx.stage.StageStyle
import tornadofx.*

class TopMenuView : View() {

    override val root = menubar {
        menu("_File") {
            item("_About") {
                action {
                    find<AboutPopup>().openModal(stageStyle = StageStyle.UTILITY, resizable = false)
                }
            }
            item("_Exit") {
                action {
                    primaryStage.requestClose()
                }
            }
        }
    }

}