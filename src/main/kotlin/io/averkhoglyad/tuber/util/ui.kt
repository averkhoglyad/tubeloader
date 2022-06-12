package io.averkhoglyad.tuber.util

import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.stage.Stage
import javafx.stage.WindowEvent
import org.controlsfx.control.StatusBar
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.Glyph

fun Stage.consumeCloseRequest(op: (Stage) -> Unit) {
    setOnCloseRequest {
        it.consume()
        op(this@consumeCloseRequest)
    }
}

fun Stage.requestClose() {
    fireEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSE_REQUEST))
}

fun fontawesome(icon: FontAwesome.Glyph, op: Glyph.() -> Unit = {}) = Glyph("FontAwesome", icon).also(op)

fun StatusBar.bindStatus(text: String, status: ReadOnlyDoubleProperty) {
    this.text = text
    progressProperty().bind(status)
}

fun StatusBar.clearStatus() {
    text = ""
    progressProperty().unbind()
    progress = 0.0
}

fun <E> ListView<E>.modifyCell(fn: (ListCell<E>) -> Unit) {
    val originalCellFactory = cellFactory
    setCellFactory { param -> originalCellFactory.call(param).also(fn) }
}
